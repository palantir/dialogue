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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 3)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
public class NodeSelectionBenchmark {

    @Param({"PIN_UNTIL_ERROR", "ROUND_ROBIN"})
    public NodeSelectionStrategy strategy;

    @Param({"2", "8", "100"})
    public int numChannels;

    private final Request request = Request.builder().build();
    private LimitedChannel channel;

    @Setup(Level.Invocation)
    public void setUp() {
        ImmutableList<LimitedChannel> channels = IntStream.range(0, numChannels)
                .mapToObj(i -> AlwaysLimited.INSTANCE)
                .collect(ImmutableList.toImmutableList());

        DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        Random random = SafeThreadLocalRandom.get();
        Ticker ticker = Ticker.systemTicker();

        switch (strategy) {
            case PIN_UNTIL_ERROR:
                channel = PinUntilErrorNodeSelectionStrategyChannel.of(
                        Optional.empty(),
                        DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR,
                        channels,
                        DialoguePinuntilerrorMetrics.of(metrics),
                        random,
                        ticker,
                        "channelName");
                break;
            case ROUND_ROBIN:
                channel = new BalancedNodeSelectionStrategyChannel(channels, random, ticker, metrics, "channelName");
                break;
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                throw new SafeIllegalArgumentException("Unsupported");
        }
    }

    @Benchmark
    public Optional<ListenableFuture<Response>> postRequest() {
        return channel.maybeExecute(TestEndpoint.POST, request);
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(NodeSelectionBenchmark.class.getSimpleName())
                // .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }

    private enum AlwaysLimited implements LimitedChannel {
        INSTANCE;

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint _endpoint, Request _request) {
            return Optional.empty();
        }
    }
}
