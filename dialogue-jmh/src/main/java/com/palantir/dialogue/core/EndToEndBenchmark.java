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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
public class EndToEndBenchmark {

    private Undertow undertow;
    private ExecutorService blockingExecutor;

    private SampleServiceBlocking blocking;
    private SampleServiceAsync async;

    @Setup
    public void before() {
        undertow = Undertow.builder()
                // .addHttpListener(0, "localhost", new BlockingHandler(exchange -> exchange.setStatusCode(200)))
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

        DialogueClients.ReloadingFactory clients = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withTaggedMetrics(metrics)
                .withBlockingExecutor(blockingExecutor);

        ServiceConfiguration serviceConf = ServiceConfiguration.builder()
                .addUris(getUri(undertow))
                .security(TestConfigurations.SSL_CONFIG)
                .build();

        blocking = clients.getNonReloading(SampleServiceBlocking.class, serviceConf);
        async = clients.getNonReloading(SampleServiceAsync.class, serviceConf);
    }

    @TearDown
    public void after() {
        undertow.stop();
        MoreExecutors.shutdownAndAwaitTermination(RetryingChannel.sharedScheduler.get(), 1, TimeUnit.SECONDS);
        MoreExecutors.shutdownAndAwaitTermination(blockingExecutor, 1, TimeUnit.SECONDS);
    }

    @Benchmark
    public void blocking() {
        blocking.voidToVoid();
    }

    @Benchmark
    public ListenableFuture<Void> async() {
        return async.voidToVoid();
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(EndToEndBenchmark.class.getSimpleName())
                .jvmArgsAppend("-XX:+CrashOnOutOfMemoryError")
                // .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
