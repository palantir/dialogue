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

package com.palantir.dialogue.otherpackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory2;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.logsafe.Preconditions;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.immutables.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DialogueClientsIntegrationTest {
    private static final String FOO_SERVICE = "foo";
    private static final Splitter PATH_SPLITTER = Splitter.on("/");
    private ServiceConfiguration serviceConfig;
    private Undertow undertow;
    private HttpHandler undertowHandler;
    private ServicesConfigBlock scb;
    private PartialServiceConfiguration foo1;
    private PartialServiceConfiguration foo2;
    private PartialServiceConfiguration threeFoos;
    private final String foo1Path = "/foo1";
    private final String foo2Path = "/foo2";
    private final String foo3Path = "/foo3";

    @BeforeEach
    public void before() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        undertow = Undertow.builder()
                .addHttpsListener(
                        0,
                        "localhost",
                        sslContext,
                        new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
        serviceConfig = ServiceConfiguration.builder()
                .security(TestConfigurations.SSL_CONFIG)
                .addUris(getUri(undertow))
                .build();
        foo1 = PartialServiceConfiguration.builder()
                .addUris(getUri(undertow) + foo1Path)
                .build();
        foo2 = PartialServiceConfiguration.builder()
                .addUris(getUri(undertow) + foo2Path)
                .build();
        threeFoos = PartialServiceConfiguration.builder()
                .addUris(getUri(undertow) + foo1Path)
                .addUris(getUri(undertow) + foo2Path)
                .addUris(getUri(undertow) + foo3Path)
                .build();
        scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices("foo", foo1)
                .build();
    }

    @AfterEach
    public void after() {
        undertow.stop();
    }

    @Test
    void throws_if_user_agent_is_missing() {
        assertThatThrownBy(() -> DialogueClients.create(Refreshable.only(null))
                        .getNonReloading(SampleServiceAsync.class, serviceConfig))
                .hasMessageContaining("userAgent must be specified");
    }

    @Test
    void reload_uris_works() {
        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };

        SettableRefreshable<ServicesConfigBlock> refreshable = Refreshable.create(scb);
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(refreshable).withUserAgent(TestConfigurations.AGENT);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
        client.voidToVoid();

        refreshable.update(
                ServicesConfigBlock.builder().from(scb).putServices("foo", foo2).build());

        client.voidToVoid();
        assertThat(requestPaths).containsExactly("/foo1/voidToVoid", "/foo2/voidToVoid");
    }

    @Test
    void custom_dns_resolver() {
        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };
        String randomHostname = UUID.randomUUID().toString();
        String uri = (getUri(undertow) + foo1Path).replace("localhost", randomHostname);
        assertThat(URI.create(uri)).hasHost(randomHostname);
        DialogueClients.ReloadingFactory factory = DialogueClients.create(Refreshable.only(ServicesConfigBlock.builder()
                        .defaultSecurity(TestConfigurations.SSL_CONFIG)
                        .putServices(
                                "foo",
                                PartialServiceConfiguration.builder()
                                        .addUris(uri)
                                        .build())
                        .build()))
                .withUserAgent(TestConfigurations.AGENT)
                .withDnsResolver(hostname -> {
                    if (randomHostname.equals(hostname)) {
                        try {
                            return ImmutableSet.of(InetAddress.getByAddress(randomHostname, new byte[] {127, 0, 0, 1}));
                        } catch (UnknownHostException ignored) {
                            // fall-through
                        }
                    }
                    return ImmutableSet.of();
                });

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
        client.voidToVoid();
        assertThat(requestPaths).containsExactly("/foo1/voidToVoid");
    }

    @Test
    void test_legacy_static_factory() {
        List<String> requestPaths = new CopyOnWriteArrayList<>();
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };

        SSLSocketFactory socketFactory = SslSocketFactories.createSslSocketFactory(TestConfigurations.SSL_CONFIG);
        X509TrustManager trustManager = SslSocketFactories.createX509TrustManager(TestConfigurations.SSL_CONFIG);

        ClientConfiguration config = ClientConfigurations.of(
                ImmutableList.of(getUri(undertow) + foo1Path), socketFactory, trustManager, TestConfigurations.AGENT);
        SampleServiceBlocking client = DialogueClients.create(SampleServiceBlocking.class, config);
        client.voidToVoid();
        assertThat(requestPaths).containsExactly("/foo1/voidToVoid");
    }

    @Test
    void building_non_reloading_clients_always_gives_the_same_instance() {
        AtomicInteger statusCode = new AtomicInteger(200);
        Set<String> requestPaths = ConcurrentHashMap.newKeySet();
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(statusCode.get());
        };

        serviceConfig = ServiceConfiguration.builder()
                .security(TestConfigurations.SSL_CONFIG)
                .addUris(IntStream.range(0, 100)
                        .mapToObj(i -> getUri(undertow) + "/api" + i)
                        .toArray(String[]::new))
                .maxNumRetries(0)
                .build();

        DialogueClients.ReloadingFactory factory = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withNodeSelectionStrategy(NodeSelectionStrategy.PIN_UNTIL_ERROR);

        SampleServiceBlocking instance = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        SampleServiceBlocking instance2 = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        SampleServiceBlocking instance3 = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        instance.voidToVoid();
        instance2.voidToVoid();
        instance3.voidToVoid();
        assertThat(requestPaths)
                .describedAs("Out of the hundred urls, each of these clients should start off pinned to the same host")
                .hasSize(1);
        statusCode.set(503);
        assertThatThrownBy(instance::voidToVoid).isInstanceOf(QosException.Unavailable.class);
        assertThatThrownBy(instance2::voidToVoid).isInstanceOf(QosException.Unavailable.class);
        assertThatThrownBy(instance3::voidToVoid).isInstanceOf(QosException.Unavailable.class);
        assertThat(requestPaths).hasSize(3);
    }

    @Test
    void test_conn_timeout_with_unlimited_socket_timeout() {
        undertowHandler = _exchange -> Thread.sleep(1_000L);
        SampleServiceBlocking client = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getNonReloading(
                        SampleServiceBlocking.class,
                        ServiceConfiguration.builder()
                                .addUris(getUri(undertow))
                                .security(TestConfigurations.SSL_CONFIG)
                                .connectTimeout(Duration.ofMillis(300))
                                // socket timeouts use zero as a sentinel for unlimited duration
                                .readTimeout(Duration.ZERO)
                                .writeTimeout(Duration.ZERO)
                                .build());
        assertThatCode(client::voidToVoid)
                .as("initial request should not throw")
                .doesNotThrowAnyException();
        assertThatCode(client::voidToVoid)
                .as("subsequent requests reusing the connection should not throw")
                .doesNotThrowAnyException();
    }

    @Test
    void test_unlimited_timeouts() {
        undertowHandler = _exchange -> Thread.sleep(1_000L);
        SampleServiceBlocking client = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getNonReloading(
                        SampleServiceBlocking.class,
                        ServiceConfiguration.builder()
                                .addUris(getUri(undertow))
                                .security(TestConfigurations.SSL_CONFIG)
                                // socket timeouts use zero as a sentinel for unlimited duration
                                .connectTimeout(Duration.ZERO)
                                .readTimeout(Duration.ZERO)
                                .writeTimeout(Duration.ZERO)
                                .build());
        assertThatCode(client::voidToVoid)
                .as("initial request should not throw")
                .doesNotThrowAnyException();
        assertThatCode(client::voidToVoid)
                .as("subsequent requests reusing the connection should not throw")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_sticky_is_sticky() {
        testSticky(ReloadingFactory::getStickyChannels, StickyChannelFactory::getCurrentBest);
    }

    @Test
    public void test_sticky2_is_sticky() {
        testSticky(ReloadingFactory::getStickyChannels2, StickyChannelFactory2::sticky);
    }

    @Test
    public void test_sticky2_session_is_sticky() {
        testSticky(
                ReloadingFactory::getStickyChannels2,
                (stickyChannelFactory2, sampleServiceAsyncClass) ->
                        stickyChannelFactory2.session().sticky(sampleServiceAsyncClass));
    }

    private <F> void testSticky(
            BiFunction<ReloadingFactory, String, F> factoryFactory,
            BiFunction<F, Class<SampleServiceAsync>, SampleServiceAsync> clientFactory) {
        List<StringToVoidRequestPath> requestPaths = Collections.synchronizedList(new ArrayList<>());
        int maxConcurrentRequestsPerServer = 10;
        Map<String, Integer> activeRequestsPerServer = new ConcurrentHashMap<>();

        undertowHandler = exchange -> {
            String requestPath = exchange.getRequestPath();
            StringToVoidRequestPath path = parse(requestPath);
            String server = path.requestPath();
            try {
                int activeRequests = activeRequestsPerServer.compute(server, (_ignore, activeRequests1) -> {
                    if (activeRequests1 == null) {
                        return 1;
                    } else {
                        return activeRequests1 + 1;
                    }
                });
                if (activeRequests > maxConcurrentRequestsPerServer) {
                    exchange.setStatusCode(200);
                } else {
                    exchange.setStatusCode(429);
                }
            } finally {
                activeRequestsPerServer.compute(server, (_ignore, activeRequests12) -> {
                    Preconditions.checkNotNull(activeRequests12, "activeRequests");
                    Preconditions.checkState(activeRequests12 > 0, "activeRequests");
                    return activeRequests12 - 1;
                });
                requestPaths.add(path);
            }
        };

        SettableRefreshable<ServicesConfigBlock> refreshable = Refreshable.create(ServicesConfigBlock.builder()
                .from(scb)
                .putServices(FOO_SERVICE, threeFoos)
                .build());
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(refreshable).withUserAgent(TestConfigurations.AGENT);

        F stickyChannels = factoryFactory.apply(factory, FOO_SERVICE);

        int numClients = 3;
        int numRequestPerClient = 1000;

        List<ListenableFuture<?>> requests = new ArrayList<>();
        for (int i = 0; i < numClients; i++) {
            SampleServiceAsync client = clientFactory.apply(stickyChannels, SampleServiceAsync.class);
            String clientId = Integer.toString(i);
            IntStream.range(0, numRequestPerClient).forEach(_ignore -> requests.add(client.stringToVoid(clientId)));
        }

        assertThat(Futures.whenAllComplete(requests).run(() -> {}, MoreExecutors.directExecutor()))
                .succeedsWithin(Duration.ofMinutes(1));

        assertThat(requestPaths).hasSizeGreaterThanOrEqualTo(numClients * numRequestPerClient);
        Set<StringToVoidRequestPath> uniquePaths = new HashSet<>(requestPaths);
        assertThat(uniquePaths).hasSize(numClients);

        // *I think* this technically has a chance to flake, but let's see how it goes. I am trying to make sure the
        // requests are actually being pinned and not just because all the requests went to a single node.
        assertThat(uniquePaths.stream().map(StringToVoidRequestPath::server)).hasSizeGreaterThanOrEqualTo(2);

        List<String> clientIds =
                uniquePaths.stream().map(StringToVoidRequestPath::client).collect(Collectors.toList());

        assertThat(clientIds).containsExactlyInAnyOrder("0", "1", "2");
    }

    private static StringToVoidRequestPath parse(String requestPath) {
        List<String> segments = PATH_SPLITTER.splitToList(requestPath);
        assertThat(segments.get(0)).isEmpty();
        assertThat(Collections.singleton(segments.get(1))).containsAnyOf("foo1", "foo2", "foo3");
        assertThat(segments.get(2)).isEqualTo("stringToVoid");
        return ImmutableStringToVoidRequestPath.of(requestPath, segments.get(1), segments.get(3));
    }

    @Value.Immutable
    interface StringToVoidRequestPath {
        @Value.Parameter
        String requestPath();

        @Value.Parameter
        String server();

        @Value.Parameter
        String client();
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
