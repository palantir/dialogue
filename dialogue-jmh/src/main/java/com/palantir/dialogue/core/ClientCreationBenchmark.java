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

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.example.ManyEndpointsServiceAsync;
import com.palantir.dialogue.example.ManyEndpointsServiceBlocking;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@Warmup(iterations = 15, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
public class ClientCreationBenchmark {

    private Undertow undertow;
    private ExecutorService blockingExecutor;

    private DialogueClients.StickyChannelFactory2 stickyChannelFactory2;

    @Setup
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(0, "localhost", new ResponseCodeHandler(200))
                .build();
        undertow.start();

        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();

        blockingExecutor = Tracers.wrap(
                "dialogue-blocking-channel",
                Executors.newCachedThreadPool(MetricRegistries.instrument(
                        metrics,
                        new ThreadFactoryBuilder()
                                .setNameFormat("dialogue-blocking-channel-%d")
                                .setDaemon(true)
                                .build(),
                        "dialogue-blocking-channel")));

        stickyChannelFactory2 = DialogueClients.create(Refreshable.only(ServicesConfigBlock.builder()
                        .defaultSecurity(SslConfiguration.builder()
                                .trustStorePath(Paths.get("../dialogue-test-common/src/main/resources/trustStore.jks"))
                                .build())
                        .putServices(
                                "test",
                                PartialServiceConfiguration.builder()
                                        .addUris(getUri(undertow))
                                        .build())
                        .build()))
                .withUserAgent(TestConfigurations.AGENT)
                .withTaggedMetrics(metrics)
                .withBlockingExecutor(blockingExecutor)
                .withNodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .getStickyChannels2("test");
    }

    @TearDown
    public void after() throws IOException {
        undertow.stop();
        MoreExecutors.shutdownAndAwaitTermination(RetryingChannel.sharedScheduler.get(), 1, TimeUnit.SECONDS);
        MoreExecutors.shutdownAndAwaitTermination(blockingExecutor, 1, TimeUnit.SECONDS);
    }

    @Benchmark
    public SampleServiceBlocking sampleServiceBlocking() {
        DialogueClients.StickyChannelSession stickySession = stickyChannelFactory2.session();
        return stickySession.sticky(SampleServiceBlocking.class);
    }

    @Benchmark
    public SampleServiceAsync sampleServiceAsync() {
        DialogueClients.StickyChannelSession stickySession = stickyChannelFactory2.session();
        return stickySession.sticky(SampleServiceAsync.class);
    }

    @Benchmark
    public ManyEndpointsServiceBlocking manyEndpointsServiceBlocking() {
        DialogueClients.StickyChannelSession stickySession = stickyChannelFactory2.session();
        return stickySession.sticky(ManyEndpointsServiceBlocking.class);
    }

    @Benchmark
    public ManyEndpointsServiceAsync manyEndpointsServiceAsync() {
        DialogueClients.StickyChannelSession stickySession = stickyChannelFactory2.session();
        return stickySession.sticky(ManyEndpointsServiceAsync.class);
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ClientCreationBenchmark.class.getSimpleName())
                .jvmArgsPrepend("-Xmx1024m", "-Xms1024m", "-XX:+CrashOnOutOfMemoryError")
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
