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
//
// package com.palantir.dialogue.core;
//
// import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
//
// import com.google.common.collect.ImmutableList;
// import com.google.common.util.concurrent.FutureCallback;
// import com.google.common.util.concurrent.Futures;
// import com.google.common.util.concurrent.ListenableFuture;
// import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
// import com.palantir.dialogue.Channel;
// import com.palantir.dialogue.ConjureRuntime;
// import com.palantir.dialogue.EndpointChannelFactory;
// import com.palantir.dialogue.Response;
// import com.palantir.dialogue.TestResponse;
// import com.palantir.dialogue.example.SampleServiceAsync;
// import com.palantir.dialogue.example.SampleServiceBlocking;
// import com.palantir.dialogue.futures.DialogueFutures;
// import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Random;
// import java.util.concurrent.atomic.AtomicLong;
// import java.util.function.Supplier;
// import org.junit.jupiter.api.Test;
//
// @SuppressWarnings("FutureReturnValueIgnored") // intentionally kicking off requests without caring about responses
// class StickyEndpointChannelsTest {
//
//     private final AtomicLong ticker = new AtomicLong();
//     private final List<String> requests = new ArrayList<>();
//     private final List<String> responses = new ArrayList<>();
//     private final ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
//
//     private final Supplier<ListenableFuture<Response>> serve204 =
//             () -> Futures.immediateFuture(new TestResponse().code(204));
//     private final Supplier<ListenableFuture<Response>> immediate429 =
//             () -> Futures.immediateFuture(new TestResponse().code(429));
//     private final Supplier<ListenableFuture<Response>> immediate503 =
//             () -> Futures.immediateFuture(new TestResponse().code(503));
//
//     private StickyEndpointChannels.Builder builder() {
//         return StickyEndpointChannels.builder()
//                 .ticker(ticker::getAndIncrement)
//                 .taggedMetricRegistry(new DefaultTaggedMetricRegistry())
//                 .channelName("channelName");
//     }
//
//     @Test
//     void all_calls_on_a_sticky_channel_go_to_one_host() {
//         StickyEndpointChannels channels = builder()
//                 .channel(ImmutableList.of(
//                         miniServer("one", serve204), miniServer("two", serve204), miniServer("three", serve204)))
//                 .build();
//
//         Channel sticky1 = channels.get();
//
//         SampleServiceAsync async1 = SampleServiceAsync.of(sticky1, runtime);
//         async1.voidToVoid();
//         async1.getMyAlias();
//         async1.getOptionalBinary();
//         SampleServiceBlocking blocking1 = SampleServiceBlocking.of(sticky1, runtime);
//         blocking1.voidToVoid();
//         blocking1.voidToVoid();
//
//         assertThat(requests)
//                 .describedAs("All requests should go to the same randomly chosen host, in this case 'three'")
//                 .allSatisfy(string -> assertThat(string).startsWith("[three]"));
//         requests.clear();
//
//         Channel sticky2 = channels.get();
//         SampleServiceAsync async2 = SampleServiceAsync.of(sticky2, runtime);
//         async2.voidToVoid();
//         async2.getMyAlias();
//         async2.getOptionalBinary();
//
//         assertThat(requests)
//                 .describedAs("Second batch of requests should all go to another randomly chosen host")
//                 .allSatisfy(string -> assertThat(string).startsWith("[two]"));
//     }
//
//     @Test
//     void sticky_channel_stays_put_despite_503s() {
//         StickyEndpointChannels channels = builder()
//                 .channel(ImmutableList.of(
//                         miniServer("one", serve204), miniServer("two", serve204), miniServer("three", immediate503)))
//                 .build();
//
//         SampleServiceAsync async1 = SampleServiceAsync.of(channels.get(), runtime);
//         async1.voidToVoid();
//         async1.getMyAlias();
//         async1.getOptionalBinary();
//
//         assertThat(responses)
//                 .describedAs("We chose channel [three] randomly, and stay pinned so that a transaction has the best "
//                         + "chance of completing")
//                 .containsExactly("[three] 503", "[three] 503", "[three] 503");
//         requests.clear();
//
//         for (int i = 0; i < 200; i++) {
//             SampleServiceAsync async = SampleServiceAsync.of(channels.get(), runtime);
//             async.voidToVoid();
//             async.getMyAlias();
//             async.getOptionalBinary();
//         }
//
//         assertThat(requests)
//                 .describedAs("The BalancedScoreTracker understands that channel [three] is "
//                         + "bad (returning 503s), so 200 more 'transactions' are routed to [one] or [two]")
//                 .allSatisfy(string -> assertThat(string).doesNotContain("[three]"));
//     }
//
//     @Test
//     void sticky_channel_stays_put_despite_429s() {
//         StickyEndpointChannels channels = builder()
//                 .channel(ImmutableList.of(
//                         miniServer("one", serve204), miniServer("two", serve204), miniServer("three", immediate429)))
//                 .build();
//
//         SampleServiceAsync async1 = SampleServiceAsync.of(channels.get(), runtime);
//         async1.voidToVoid();
//         async1.getMyAlias();
//         async1.getOptionalBinary();
//
//         assertThat(responses)
//                 .describedAs("We chose channel [three] randomly, and stay pinned so that a transaction has the best "
//                         + "chance of completing")
//                 .containsExactly("[three] 429", "[three] 429", "[three] 429");
//         requests.clear();
//
//         for (int i = 0; i < 2; i++) {
//             SampleServiceAsync async = SampleServiceAsync.of(channels.get(), runtime);
//             async.voidToVoid();
//             async.getMyAlias();
//             async.getOptionalBinary();
//         }
//
//         assertThat(requests)
//                 .describedAs("The BalancedScoreTracker understands that channel [three] is "
//                         + "bad (returning 503s), so 200 more 'transactions' are routed to [one] or [two]")
//                 .allSatisfy(string -> assertThat(string).doesNotContain("[three]"));
//     }
//
//     private EndpointChannelFactory miniServer(String serverName, Supplier<ListenableFuture<Response>> response) {
//         return endpoint -> _request -> {
//             requests.add(String.format("[%s] [%s]", serverName, endpoint.endpointName()));
//             ListenableFuture<Response> future = response.get();
//             DialogueFutures.addDirectCallback(future, new FutureCallback<Response>() {
//                 @Override
//                 public void onSuccess(Response result) {
//                     responses.add(String.format("[%s] %s", serverName, result.code()));
//                 }
//
//                 @Override
//                 public void onFailure(Throwable throwable) {
//                     responses.add(String.format("[%s] %s", serverName, throwable.toString()));
//                 }
//             });
//             return future;
//         };
//     }
// }
