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
package com.palantir.dialogue.hc5;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.dialogue.serde.Encodings;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Client {
    static {
        LoggerBindings.initialize();
    }

    private static final Logger log = LoggerFactory.getLogger(Client.class);
    private static final UserAgent AGENT = UserAgent.of(UserAgent.Agent.of("repro", "0.0.1"));
    private static final int PORT = 8443;
    private static final int THREADS = 32;
    private static final AtomicLong sent = new AtomicLong();
    private static final AtomicLong success = new AtomicLong();
    private static final byte[] responseData =
            ('"' + Strings.repeat("Hello, World!", 1) + '"').getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) {
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(
                        ImmutableList.of("https://localhost:" + PORT),
                        SslSocketFactories.createSslSocketFactory(sslConfig),
                        SslSocketFactories.createX509TrustManager(sslConfig)))
                .enableGcmCipherSuites(true)
                .backoffSlotSize(Duration.ZERO)
                .maxNumRetries(100)
                .clientQoS(ClientConfiguration.ClientQoS.DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS)
                .userAgent(AGENT)
                .hostEventsSink(NoOpHostEventsSink.INSTANCE)
                .build();
        // ApacheHttpClientChannels.CloseableClient cclient =
        //         ApacheHttpClientChannels.createCloseableHttpClient(config, "repro");
        // Channel channel = ApacheHttpClientChannels.createSingleUri("https://localhost:" + PORT, cclient);
        Channel channel = ApacheHttpClientChannels.create(config, "repro");
        SimpleServiceBlocking blocking = SimpleServiceBlocking.of(
                channel,
                DefaultConjureRuntime.builder().encodings(Encodings.json()).build());
        // This client uses an older okhttp version with http/2 bugs. While it doesn't represent ideal state,
        // the server should gracefully handle incorrect inputs.
        //        SimpleService client = JaxRsClient.create(
        //                SimpleService.class,
        //                AGENT,
        //                NoOpHostEventsSink.INSTANCE,
        //                config);

        ExecutorService executor = Executors.newCachedThreadPool();
        List<Thread> threads = new CopyOnWriteArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            executor.execute(() -> {
                threads.add(Thread.currentThread());
                while (true) {
                    sent.incrementAndGet();
                    // reset interruption
                    Thread.interrupted();
                    try (InputStream is = blocking.ping(responseData)) {
                        ByteStreams.exhaust(is);
                        success.incrementAndGet();
                    } catch (RuntimeException | IOException e) {
                        if (e.getCause() instanceof InterruptedException) {
                            continue;
                        }
                        // interruption cancels requests, which can fail in interesting ways upon interruption.
                        // We're interested in how the server responds in these cases.
                        String message = e.getMessage();
                        if (message == null || !message.contains("cancelled via interruption")) {
                            log.warn("client failure", e);
                        }
                    }
                }
            });
        }
        int iterations = 0;
        while (true) {
            iterations++;
            if (iterations % 500 == 0) {
                log.info("Total client requests: {} successful requests: {}", sent.get(), success.get());
                String leaks = SharedTaggedMetricRegistries.getSingleton().getMetrics().entrySet().stream()
                        .filter(entry -> "dialogue.client.response.leak"
                                .equals(entry.getKey().safeName()))
                        .map(entry -> entry.getKey() + ": " + ((Meter) entry.getValue()).getCount())
                        .collect(Collectors.joining(", "));
                log.info("LEAKS: {}", leaks);
                String leased = SharedTaggedMetricRegistries.getSingleton().getMetrics().entrySet().stream()
                        .filter(entry -> "dialogue.client.pool.size"
                                        .equals(entry.getKey().safeName())
                                && "leased".equals(entry.getKey().safeTags().get("state")))
                        .map(entry -> entry.getKey() + ": " + ((Gauge) entry.getValue()).getValue())
                        .collect(Collectors.joining(", "));
                log.info("LEASED: {}", leased);
            }
            // Depending on hardware, the delay duration may need to scale up or down. If too few requests are able to
            // successfully complete, we don't get a good distribution of interruptions across the request lifespan
            // and fail to reproduce bugs, but if too many complete successfully there are too few opportunities
            // to trigger the race.
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(10));
            Thread randomThread = threads.get(ThreadLocalRandom.current().nextInt(threads.size()));
            randomThread.interrupt();
        }
    }

    @Path("/simple")
    @Produces("application/json")
    @Consumes("application/json")
    public interface SimpleService {

        @POST
        @Path("/ping")
        void ping(byte[] data);
    }

    public interface SimpleServiceAsync {

        ListenableFuture<InputStream> ping(byte[] data);

        static SimpleServiceAsync of(EndpointChannelFactory _endpointChannelFactory, ConjureRuntime _runtime) {
            return new SimpleServiceAsync() {
                private final Deserializer<InputStream> pingDeserializer =
                        _runtime.bodySerDe().inputStreamDeserializer();

                private final Serializer<byte[]> postNumbersSerializer =
                        _runtime.bodySerDe().serializer(new TypeMarker<byte[]>() {});

                private final EndpointChannel pingChannel = _endpointChannelFactory.endpoint(new Endpoint() {
                    @Override
                    public void renderPath(Map<String, String> params, UrlBuilder url) {
                        url.pathSegment("ping");
                    }

                    @Override
                    public HttpMethod httpMethod() {
                        return HttpMethod.POST;
                    }

                    @Override
                    public String serviceName() {
                        return "SimpleService";
                    }

                    @Override
                    public String endpointName() {
                        return "ping";
                    }

                    @Override
                    public String version() {
                        return "0.1.2";
                    }
                });

                @Override
                public ListenableFuture<InputStream> ping(byte[] data) {
                    Request.Builder request = Request.builder().body(postNumbersSerializer.serialize(data));
                    return _runtime.clients().call(pingChannel, request.build(), pingDeserializer);
                }
            };
        }

        static SimpleServiceAsync of(Channel _channel, ConjureRuntime _runtime) {
            if (_channel instanceof EndpointChannelFactory) {
                return of((EndpointChannelFactory) _channel, _runtime);
            }
            return of(endpoint -> _runtime.clients().bind(_channel, endpoint), _runtime);
        }
    }

    public interface SimpleServiceBlocking {

        InputStream ping(byte[] data);

        static SimpleServiceBlocking of(EndpointChannelFactory _endpointChannelFactory, ConjureRuntime _runtime) {
            SimpleServiceAsync delegate = SimpleServiceAsync.of(_endpointChannelFactory, _runtime);
            return new SimpleServiceBlocking() {
                @Override
                public InputStream ping(byte[] data) {
                    return _runtime.clients().block(delegate.ping(data));
                }
            };
        }

        static SimpleServiceBlocking of(Channel _channel, ConjureRuntime _runtime) {
            if (_channel instanceof EndpointChannelFactory) {
                return of((EndpointChannelFactory) _channel, _runtime);
            }
            return of(endpoint -> _runtime.clients().bind(_channel, endpoint), _runtime);
        }
    }

    public enum NoOpHostEventsSink implements HostEventsSink {
        INSTANCE;

        @Override
        public void record(String _serviceName, String _hostname, int _port, int _statusCode, long _micros) {
            // do nothing
        }

        @Override
        public void recordIoException(String _serviceName, String _hostname, int _port) {
            // do nothing
        }
    }
}
