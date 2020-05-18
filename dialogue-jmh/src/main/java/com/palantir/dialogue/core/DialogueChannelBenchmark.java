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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Optional;
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
import org.openjdk.jmh.infra.Blackhole;

@Measurement(iterations = 3, time = 3)
@Warmup(iterations = 2, time = 5)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DialogueChannelBenchmark {

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        @Param({"2", "3", "10", "100", "1000"})
        public int numChannels;

        @Param({"1000"})
        public int numRequests;

        private BalancedNodeSelectionStrategyChannel target;

        @Setup(Level.Invocation)
        public void setUp() {
            LimitedChannel fakeChannel = new LimitedChannel() {
                @Override
                public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
                    return Optional.empty();
                }
            };

            ImmutableList<LimitedChannel> chans =
                    IntStream.range(0, numChannels).mapToObj(i -> fakeChannel).collect(ImmutableList.toImmutableList());

            target = new BalancedNodeSelectionStrategyChannel(
                    chans,
                    SafeThreadLocalRandom.get(),
                    Ticker.systemTicker(),
                    new DefaultTaggedMetricRegistry(),
                    "channelName");
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void fireEmOff(ExecutionPlan plan, Blackhole blackhole) {
        Request request = Request.builder().build();

        for (int i = 0; i < plan.numRequests; i++) {
            Optional<ListenableFuture<Response>> maybeFuture = plan.target.maybeExecute(TestEndpoint.POST, request);
            blackhole.consume(maybeFuture);
        }

    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
