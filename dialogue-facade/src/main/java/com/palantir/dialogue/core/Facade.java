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

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * Guiding principle: Users can't be trusted to close things to prevent OOMs, we must do it automatically for them.
 */
public final class Facade {
    private final ConjureRuntime runtime;
    private final Supplier<ScheduledExecutorService> executor;

    private Facade(ConjureRuntime runtime, Supplier<ScheduledExecutorService> executor) {
        this.runtime = runtime;
        this.executor = executor;
    }

    public static Facade create() {
        return new Facade(DefaultConjureRuntime.builder().build(), RetryingChannel.sharedScheduler);
    }

    /**
     * LIMITATIONS:
     * <ul>
     *     <li>Users have to build the client configuration themselves, which doesn't have an equals method :(
     *     <li>Users can't tweak settings like clientQos, maxNumRetries. Also can't pass in an executor.
     *     <li>No live-reloading
     *     <li>No interning, i.e. if people repeatedly ask for the same client over and over again, then
     *     requests will count against *independent* concurrency limiters (maybe this is fine???)
     *     <li>Doesn't re-use any possible existing connection pool (e.g. if basicClients are created in a hot loop),
     *     which probably leads to more overhead idle connections than necessary
     *     <li>Doesn't close the apache client at any point (this is actually fine because the
     *     {@link PoolingHttpClientConnectionManager} has a finalize method!)
     *     <li>Makes a new apache http client for every single clazz, even if it talks to the same urls. Maybe this
     *     is actually fine??
     *     <li>Doesn't support jaxrs/retrofit
     * </ul>
     */
    public <T> T get(Class<T> clazz, ClientConfiguration conf) {
        String channelName = "facade-" + clazz.getSimpleName();
        ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, channelName);

        Channel channel = new BasicBuilder()
                .channelName(channelName)
                .clientConfiguration(conf)
                .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, client))
                .scheduler(executor.get())
                .build();

        return callStaticFactoryMethod(clazz, channel, runtime);
    }

    private Facade copy() {
        return new Facade(runtime, executor);
    }

    public Facade withExecutor(ScheduledExecutorService override) {
        return new Facade(runtime, () -> override);
    }

    public Facade withRuntime(ConjureRuntime override) {
        return new Facade(override, executor);
    }

    private static <T> T callStaticFactoryMethod(
            Class<T> dialogueInterface, Channel channel, ConjureRuntime conjureRuntime) {
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
}
