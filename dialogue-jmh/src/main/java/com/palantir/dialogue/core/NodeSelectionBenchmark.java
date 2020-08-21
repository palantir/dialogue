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

// @State(Scope.Benchmark)
// @Warmup(iterations = 12, time = 500, timeUnit = TimeUnit.MILLISECONDS)
// @Measurement(iterations = 12, time = 500, timeUnit = TimeUnit.MILLISECONDS)
// @Fork(value = 1)
// @OutputTimeUnit(TimeUnit.MILLISECONDS)
// @BenchmarkMode(Mode.Throughput)
// @SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
// public class NodeSelectionBenchmark {
//
//     @Param({"true", "false"})
//     public boolean headerDriven;
//
//     @Param({"2", "8"})
//     public int numChannels;
//
//     @Param({"PIN_UNTIL_ERROR", "ROUND_ROBIN"})
//     public NodeSelectionStrategy selectionStrategy;
//
//     private static final Request request = Request.builder().build();
//     private static final TestResponse response =
//             new TestResponse().code(200).withHeader("Node-Selection-Strategy", "BALANCED");
//     private static final ListenableFuture<Response> future = Futures.immediateFuture(response);
//
//     private final DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
//     private final Random random = SafeThreadLocalRandom.get();
//     private final Ticker ticker = Ticker.systemTicker();
//
//     private LimitedChannel channel;
//
//     @Setup(Level.Invocation)
//     public void before() {
//         ImmutableList<LimitedChannel> channels = IntStream.range(0, numChannels)
//                 .mapToObj(_i -> FakeChannel.INSTANCE)
//                 .collect(ImmutableList.toImmutableList());
//
//         if (headerDriven) {
//             switch (selectionStrategy) {
//                 case PIN_UNTIL_ERROR:
//                     channel = new NodeSelectionStrategyChannel(
//                             NodeSelectionStrategyChannel::getFirstKnownStrategy,
//                             DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR,
//                             "channelName",
//                             random,
//                             ticker,
//                             metrics,
//                             channels);
//                     break;
//                 case ROUND_ROBIN:
//                     channel = new NodeSelectionStrategyChannel(
//                             NodeSelectionStrategyChannel::getFirstKnownStrategy,
//                             DialogueNodeSelectionStrategy.BALANCED,
//                             "channelName",
//                             random,
//                             ticker,
//                             metrics,
//                             channels);
//                     break;
//                 default:
//                     throw new SafeIllegalArgumentException("Unsupported");
//             }
//         } else {
//             switch (selectionStrategy) {
//                 case PIN_UNTIL_ERROR:
//                     channel = PinUntilErrorNodeSelectionStrategyChannel.of(
//                             Optional.empty(),
//                             DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR,
//                             channels,
//                             DialoguePinuntilerrorMetrics.of(metrics),
//                             random,
//                             ticker,
//                             "channelName");
//                     break;
//                 case ROUND_ROBIN:
//                     channel = new BalancedNodeSelectionStrategyChannel(
//                             channels,
//                             new BalancedScoreTracker(channels.size(), random, ticker, metrics, "channelName"));
//                     break;
//                 default:
//                     throw new SafeIllegalArgumentException("Unsupported");
//             }
//         }
//     }
//
//     @Threads(4)
//     @Benchmark
//     public Optional<ListenableFuture<Response>> postRequest() {
//         return channel.maybeExecute(TestEndpoint.POST, request);
//     }
//
//     public static void main(String[] _args) throws Exception {
//         Options opt = new OptionsBuilder()
//                 .include(NodeSelectionBenchmark.class.getSimpleName())
//                 .jvmArgsPrepend("-Xmx1024m", "-Xms1024m", "-XX:+CrashOnOutOfMemoryError")
//                 // .jvmArgsPrepend("-XX:+FlightRecorder", "-XX:StartFlightRecording=filename=./foo.jfr")
//                 // .addProfiler(GCProfiler.class)
//                 .build();
//         new Runner(opt).run();
//     }
//
//     private enum FakeChannel implements LimitedChannel {
//         INSTANCE;
//
//         @Override
//         public Optional<ListenableFuture<Response>> maybeExecute(Endpoint _endpoint, Request _request) {
//             return Optional.of(future);
//         }
//     }
// }
