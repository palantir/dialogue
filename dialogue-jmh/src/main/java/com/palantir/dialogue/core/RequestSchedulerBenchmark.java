/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.FairQueuedChannel.EventProcessorImpl;
import com.palantir.dialogue.core.FairQueuedChannel.FairQueue1;
import com.palantir.dialogue.core.FairQueuedChannel.QueueSize;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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

@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
public class RequestSchedulerBenchmark {

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({"100", "1000"})
        public int numQueues;

        @Param({"2", "3"})
        public int numHosts;

        @Param({"1", "10"})
        public int numSlotsPerHostPerIteration;

        public EventProcessorImpl eventProcessorImpl;

        public SchedulingChannel schedulingChannel;

        @Setup(Level.Trial)
        public void setUpTrial() {
            eventProcessorImpl = new EventProcessorImpl(
                    new NeverThrowLimitedChannel(schedulingChannel),
                    "channel",
                    () -> {},
                    new QueueSize(
                            QueuedChannel.channelInstrumentation(
                                    DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "channel"),
                            1_000_000),
                    new FairQueue1());

            // Enqueue a lot of requests.
            //            requestScheduler.enqueue();
        }
    }

    private static class SchedulingChannel implements LimitedChannel {

        private static final Optional<ListenableFuture<Response>> RESPONSE =
                Optional.of(Futures.immediateFuture(new TestResponse()));
        private final int maxPerHost;
        private final int[] numScheduled;

        SchedulingChannel(int maxPerHost, int numHosts) {
            this.maxPerHost = maxPerHost;
            this.numScheduled = new int[numHosts];
        }

        void reset() {
            Arrays.fill(numScheduled, 0);
        }

        @Override
        @SuppressWarnings("NullAway")
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint _endpoint, Request request) {
            HostId hostId = request.attachments().getOrDefault(RoutingAttachments.HOST_KEY, HostId.anyHost());
            int hostIdValue = hostId.value() + 1;
            int curNumScheduled = numScheduled[hostIdValue];
            if (curNumScheduled > maxPerHost) {
                return Optional.empty();
            } else {
                numScheduled[hostIdValue] = curNumScheduled + 1;
                return RESPONSE;
            }
        }
    }

    @Benchmark
    @Warmup(iterations = 32, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 16, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Fork(value = 1)
    public int benchmarkSchedule(BenchmarkState state) {
        return state.eventProcessorImpl.dispatchRequests();
    }
}
