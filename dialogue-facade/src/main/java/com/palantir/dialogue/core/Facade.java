/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.dialogue.core;

import com.google.errorprone.annotations.Immutable;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Provider;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.immutables.value.Value;

/**
 * Guiding principle: Users can't be trusted to close things to prevent OOMs, we must do it automatically for them.
 * It should be impossible to leak threads or memory by calling methods of this class.
 */
// TODO(dfox): come up with a better name
@Immutable
public final class Facade {
    private final ImmutableParams params;

    private Facade(BaseParams params) {
        this.params = ImmutableParams.builder().from(params).build();
    }

    public static Facade create() {
        return new Facade(ImmutableParams.builder().build());
    }

    /** This re-uses underlying apache clients (one per service in the service config block). */
    public ScbFacade withServiceConfigBlock(Refreshable<ServicesConfigBlock> scb) {
        return new ScbFacade(ImmutableParams2.builder().from(params).scb(scb).build());
    }

    public Facade withExecutor(ScheduledExecutorService executor) {
        return new Facade(params.withExecutor(executor));
    }

    public Facade withRuntime(ConjureRuntime runtime) {
        return new Facade(params.withRuntime(runtime));
    }

    public Facade withUserAgent(UserAgent agent) {
        return new Facade(params.withUserAgent(agent));
    }

    public Facade withTaggedMetrics(TaggedMetricRegistry metrics) {
        return new Facade(params.withTaggedMetrics(metrics));
    }

    /**
     * LIMITATIONS.
     * <ul>
     *     <li>No interning, i.e. if people repeatedly ask for the same client over and over again, then
     *     requests will count against *independent* concurrency limiters (maybe this is fine???)
     *     <li>Doesn't re-use any possible existing connection pool (e.g. if basicClients are created in a hot loop),
     *     which probably leads to more overhead idle connections than necessary
     *     <li>Doesn't close the apache client at any point (this is actually fine because the
     *     {@link PoolingHttpClientConnectionManager} has a finalize method!)
     *     <li>Makes a new apache http client for every single call, even if it talks to the same urls. Maybe this
     *     is actually fine??
     *     <li>Doesn't support jaxrs/retrofit
     * </ul>
     */
    public <T> T get(Class<T> clazz, ClientConfiguration conf) {
        Channel channel = getChannel("facade-basic-" + clazz.getSimpleName(), conf);

        return callStaticFactoryMethod(clazz, channel, params.runtime());
    }

    public <T> T get(Class<T> clazz, ServiceConfiguration serviceConf) {
        ClientConfiguration clientConfig = mix(serviceConf, params);
        return get(clazz, clientConfig);
    }

    /** Live-reloading version. Polls the supplier every second. */
    public <T> T get(Class<T> clazz, Refreshable<ClientConfiguration> clientConfig) {

        // this is the naive version of live-reloading, it doesn't try to do clever mutation under the hood, just
        // forgets about the old instance and makes a new one.
        Supplier<Channel> channels = clientConfig.map(conf -> {
            return getChannel("facade-reloading-", conf);
        });

        SupplierChannel channel = new SupplierChannel(channels);

        return callStaticFactoryMethod(clazz, channel, params.runtime());
    }

    private <T> Channel getChannel(String channelName, ClientConfiguration conf) {
        ClientConfiguration clientConf = mix(conf, params);

        ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(clientConf, channelName);

        return new BasicBuilder()
                .channelName(channelName)
                .clientConfiguration(clientConf)
                .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, client))
                .scheduler(params.executor())
                .build();
    }

    static <T> T callStaticFactoryMethod(Class<T> dialogueInterface, Channel channel, ConjureRuntime conjureRuntime) {
        try {
            Method method = getStaticOfMethod(dialogueInterface)
                    .orElseThrow(() -> new SafeIllegalStateException(
                            "A static of(Channel, ConjureRuntime) method on Dialogue interface is required",
                            SafeArg.of("dialogueInterface", dialogueInterface)));

            return dialogueInterface.cast(method.invoke(null, channel, conjureRuntime));

        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SafeIllegalArgumentException(
                    "Failed to reflectively construct dialogue client. Please check the "
                            + "dialogue interface class has a static of(Channel, ConjureRuntime) method",
                    e,
                    SafeArg.of("dialogueInterface", dialogueInterface));
        }
    }

    private static Optional<Method> getStaticOfMethod(Class<?> dialogueInterface) {
        try {
            return Optional.ofNullable(dialogueInterface.getMethod("of", Channel.class, ConjureRuntime.class));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    static ClientConfiguration mix(ServiceConfiguration serviceConfig, AugmentClientConfig ps) {
        ClientConfiguration.Builder builder =
                ClientConfiguration.builder().from(ClientConfigurations.of(serviceConfig));

        if (!serviceConfig.maxNumRetries().isPresent()) {
            ps.maxNumRetries().ifPresent(builder::maxNumRetries);
        }

        ps.securityProvider()
                .ifPresent(provider -> builder.sslSocketFactory(
                        SslSocketFactories.createSslSocketFactory(serviceConfig.security(), provider)));

        return mix(builder.build(), ps);
    }

    private static ClientConfiguration mix(ClientConfiguration clientConfig, AugmentClientConfig ps) {
        ClientConfiguration.Builder builder = ClientConfiguration.builder()
                .from(clientConfig)
                .userAgent(ps.userAgent())
                .taggedMetricRegistry(ps.taggedMetrics());

        ps.nodeSelectionStrategy().ifPresent(builder::nodeSelectionStrategy);
        ps.failedUrlCooldown().ifPresent(builder::failedUrlCooldown);
        ps.clientQoS().ifPresent(builder::clientQoS);
        ps.serverQoS().ifPresent(builder::serverQoS);
        ps.retryOnTimeout().ifPresent(builder::retryOnTimeout);

        return builder.build();
    }

    interface BaseParams extends AugmentClientConfig {
        @Value.Default
        default ConjureRuntime runtime() {
            return DefaultConjureRuntime.builder().build();
        }

        @Value.Default
        default ScheduledExecutorService executor() {
            return RetryingChannel.sharedScheduler.get();
        }
    }

    interface AugmentClientConfig {

        @Value.Default
        default TaggedMetricRegistry taggedMetrics() {
            return SharedTaggedMetricRegistries.getSingleton();
        }

        Optional<UserAgent> userAgent();

        Optional<NodeSelectionStrategy> nodeSelectionStrategy();

        Optional<Duration> failedUrlCooldown();

        Optional<ClientConfiguration.ClientQoS> clientQoS();

        Optional<ClientConfiguration.ServerQoS> serverQoS();

        Optional<ClientConfiguration.RetryOnTimeout> retryOnTimeout();

        Optional<Provider> securityProvider();

        /**
         * The provided value will only be respected if the corresponding field in {@link ServiceConfiguration}
         * is absent.
         */
        Optional<Integer> maxNumRetries();
    }

    @Immutable
    @Value.Style(passAnnotations = Immutable.class)
    @Value.Immutable
    interface Params extends BaseParams, AugmentClientConfig {}
}
