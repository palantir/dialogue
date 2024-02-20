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

package com.palantir.dialogue.clients;

import com.google.common.annotations.Beta;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.clients.ConjureClients;
import com.palantir.conjure.java.clients.ConjureClients.WithClientOptions;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.refreshable.Refreshable;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The {@link ReloadingFactory} abstraction allows users to declaratively construct clients for any of the services
 * named in the given {@link ServicesConfigBlock}. Client behaviour can be customized using the fluent methods from
 * {@link WithClientOptions} or {@link WithDialogueOptions}. Users may also choose to create one-off non-live reloading
 * clients.
 *
 * For maximum performance, we take great care to re-use underlying Apache connection pools as this avoids unnecessary
 * TLS handshakes. To achieve this, it is recommended to call {@link ReloadingFactory#create} once for the lifetime of
 * a JVM. There is no need to manually close clients or connection pools, this will be done automatically.
 *
 * Libraries may depend on interfaces contained in this class and/or interfaces from conjure-java-runtime's
 * {@link ConjureClients}. All other classes in this package are considered package-private implementation details
 * and are subject to change.
 */
public final class DialogueClients {

    public static ReloadingFactory create(Refreshable<ServicesConfigBlock> scb) {
        return new ReloadingClientFactory(
                ImmutableReloadingParams.builder().scb(scb).build(), ChannelCache.createEmptyCache());
    }

    /**
     * Prefer {@link #create(Refreshable)}. Using a configured {@link ReloadingFactory} should always be preferred
     * over this method, it only exists to ease migration.
     *
     * Construct an instance of the given {@code clientInterface} which can be used to make network calls to the
     * single conceptual upstream identified by {@code clientConfiguration}.
     *
     * Behaviour is undefined if {@code clientConfiguration} contains no URIs.
     *
     * @param clientInterface Dialogue client interface annotated with {@link com.palantir.dialogue.DialogueService}
     * @param clientConfiguration Configuration which <b>must</b> contain a user-agent
     */
    public static <T> T create(Class<T> clientInterface, ClientConfiguration clientConfiguration) {
        return LegacyConstruction.getNonReloading(clientInterface, clientConfiguration);
    }

    /** Parameters necessary for {@link DialogueChannel#builder()} and constructing an actual BlockingFoo instance. */
    @CheckReturnValue
    public interface WithDialogueOptions<T> {
        T withRuntime(ConjureRuntime runtime);

        /**
         * The Apache http client uses blocking socket operations, so threads from this executor will be used to wait
         * for responses. It's strongly recommended that custom executors support tracing-java. Cached executors are
         * the best fit because we use concurrency limiters to bound concurrent requests.
         */
        T withBlockingExecutor(ExecutorService blockingExecutor);
    }

    /** Low-level API. Most users won't need this, but it is necessary to construct feign-shim clients. */
    public interface ReloadingChannelFactory {
        Channel getChannel(String serviceName);
    }

    public interface NonReloadingChannelFactory {
        /**
         * Low level API.
         *
         * @deprecated should not be used going forward, prefer reloadable factories.
         */
        @Deprecated
        Channel getNonReloadingChannel(String channelName, ClientConfiguration clientConfiguration);
    }

    public interface ClientConfigurationNonReloadingClientFactory {
        /**
         * Construct an instance of the given {@code clientInterface} which can be used to make network calls to the
         * single conceptual upstream identified by {@code clientConfiguration}.
         *
         * Behaviour is undefined if {@code clientConfiguration} contains no URIs.
         *
         * @deprecated should not be used going forward, prefer reloadable factories.
         */
        @Deprecated
        <T> T getNonReloading(Class<T> clientInterface, ClientConfiguration clientConfiguration);
    }

    /** A stateful object - should only need one of these. Live reloads under the hood. */
    public interface StickyChannelFactory {
        /**
         * Returns a channel which will route all requests to a single host, even if that host returns some 429s.
         * Each successive call to this method may get a different channel (or it may return the same one).
         */
        Channel getStickyChannel();

        <T> T getCurrentBest(Class<T> clientInterface);
    }

    /** A stateful object - should only need one of these. Live reloads under the hood. */
    @Beta
    public interface StickyChannelFactory2 {
        /**
         * Returns a channel which will route all requests to a single host, even if that host returns some 429s.
         * Each successive call to this method may get a different channel (or it may return the same one).
         */
        Channel getStickyChannel();

        <T> T sticky(Class<T> clientInterface);

        /**
         * Creates a new {@link StickyChannelSession} which can be used to create multiple
         * clients bound to the same session.
         */
        StickyChannelSession session();
    }

    /**
     * Represents a single session of {@link StickyChannelFactory2} where the underlying channel will not change.
     * This is useful when multiple client interfaces must be bound to use the same host.
     */
    public interface StickyChannelSession {
        /**
         * Returns a channel which will route all requests to a single host, even if that host returns some 429s.
         * Each successive call to this method may get a different channel (or it may return the same one).
         */
        Channel getStickyChannel();

        <T> T sticky(Class<T> clientInterface);
    }

    public interface PerHostClientFactory {

        /** Single-uri channels. */
        Refreshable<List<Channel>> getPerHostChannels();

        <T> Refreshable<List<T>> getPerHost(Class<T> clientInterface);
    }

    public interface ReloadingFactory
            extends ConjureClients.ReloadingClientFactory,
                    WithClientOptions<ReloadingFactory>,
                    WithDialogueOptions<ReloadingFactory>,
                    ConjureClients.NonReloadingClientFactory,
                    ConjureClients.ToReloadingFactory<ReloadingFactory>,
                    ReloadingChannelFactory,
                    NonReloadingChannelFactory,
                    ClientConfigurationNonReloadingClientFactory {

        ReloadingFactory withDnsResolver(DialogueDnsResolver dnsResolver);

        StickyChannelFactory getStickyChannels(String serviceName);

        @Beta
        StickyChannelFactory2 getStickyChannels2(String serviceName);

        PerHostClientFactory perHost(String serviceName);
    }

    private DialogueClients() {}
}
