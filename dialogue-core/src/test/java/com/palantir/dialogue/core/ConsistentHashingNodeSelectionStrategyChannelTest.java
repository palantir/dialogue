/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingKey;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ConsistentHashingNodeSelectionStrategyChannelTest {
    private static final List<TargetUri> TARGETS =
            List.of(TargetUri.of("http://a"), TargetUri.of("http://b"), TargetUri.of("http://c"));

    @Test
    void stableAcrossOrderingAndMembershipChanges() throws Exception {
        List<TargetUri> reversed = List.of(TARGETS.get(2), TARGETS.get(1), TARGETS.get(0));
        List<TargetUri> expanded = new ArrayList<>(TARGETS);
        expanded.add(TargetUri.of("http://d"));
        Set<String> chosen = new HashSet<>();
        int moved = 0;
        for (int i = 0; i < 300; i++) {
            Request request = request(Integer.toString(i));
            String original = choose(TARGETS, request);
            chosen.add(original);
            assertThat(choose(reversed, request)).isEqualTo(original);
            String added = choose(expanded, request);
            if (!added.equals(original)) {
                moved++;
                assertThat(added).isEqualTo("http://d");
            }
            if (!original.equals("http://c")) {
                assertThat(choose(TARGETS.subList(0, 2), request)).isEqualTo(original);
            }
        }
        assertThat(chosen).hasSize(3);
        assertThat(moved).isBetween(40, 115);
    }

    @Test
    void dnsAddressesIdentifyDistinctNodes() throws Exception {
        List<TargetUri> targets = List.of(
                TargetUri.of("http://service", InetAddress.getByAddress(new byte[] {10, 0, 0, 1})),
                TargetUri.of("http://service", InetAddress.getByAddress(new byte[] {10, 0, 0, 2})));
        Set<String> chosen = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            chosen.add(choose(targets, request(Integer.toString(i))));
        }
        assertThat(chosen).hasSize(2);
    }

    @Test
    void unkeyedRequestsUseConfiguredStrategy() {
        AtomicInteger calls = new AtomicInteger();
        LimitedChannel fallback = (_endpoint, _request, _limits) -> {
            calls.incrementAndGet();
            return Optional.of(Futures.immediateFuture(new TestResponse().code(204)));
        };
        ConsistentHashingNodeSelectionStrategyChannel strategy =
                new ConsistentHashingNodeSelectionStrategyChannel(TARGETS, channels(TARGETS), fallback);
        assertThat(strategy.maybeExecute(
                        TestEndpoint.GET, Request.builder().build(), LimitedChannel.LimitEnforcement.DEFAULT_ENABLED))
                .isPresent();
        assertThat(calls).hasValue(1);
    }

    @Test
    void limitedPreferredNodeFallsBackAndAllLimitedQueues() throws Exception {
        Request request = request("limited");
        String preferred = choose(TARGETS, request);
        List<LimitedChannel> channels = new ArrayList<>();
        for (TargetUri target : TARGETS) {
            channels.add(target.uri().equals(preferred) ? (_ep, _req, _limits) -> Optional.empty() : channel(target));
        }
        ConsistentHashingNodeSelectionStrategyChannel strategy =
                new ConsistentHashingNodeSelectionStrategyChannel(TARGETS, channels, (_ep, _req, _lim) -> {
                    throw new AssertionError("Unexpected fallback");
                });
        try (Response response = strategy.maybeExecute(
                        TestEndpoint.GET, request, LimitedChannel.LimitEnforcement.DEFAULT_ENABLED)
                .orElseThrow()
                .get()) {
            assertThat(response.getFirstHeader("node").orElseThrow()).isNotEqualTo(preferred);
        }
        LimitedChannel limited = (_ep, _req, _lim) -> Optional.empty();
        ConsistentHashingNodeSelectionStrategyChannel allLimited =
                new ConsistentHashingNodeSelectionStrategyChannel(TARGETS, List.of(limited, limited, limited), limited);
        assertThat(allLimited.maybeExecute(TestEndpoint.GET, request, LimitedChannel.LimitEnforcement.DEFAULT_ENABLED))
                .isEmpty();
    }

    @Test
    void retryStateIsIsolatedAndSurvivesMembershipReordering() throws Exception {
        Request original = request("retry");
        Request first = ConsistentHashingNodeSelectionStrategyChannel.prepareRequest(original);
        Request second = ConsistentHashingNodeSelectionStrategyChannel.prepareRequest(original);
        String preferred = choose(TARGETS, original);
        List<LimitedChannel> channels = TARGETS.stream()
                .map(target -> target.uri().equals(preferred)
                        ? (LimitedChannel) (_ep, _req, _limits) ->
                                Optional.of(Futures.immediateFailedFuture(new java.io.IOException("test failure")))
                        : channel(target))
                .toList();
        ConsistentHashingNodeSelectionStrategyChannel strategy =
                new ConsistentHashingNodeSelectionStrategyChannel(TARGETS, channels, channels.get(0));
        com.google.common.util.concurrent.ListenableFuture<Response> failed = strategy.maybeExecute(
                        TestEndpoint.GET, first, LimitedChannel.LimitEnforcement.DEFAULT_ENABLED)
                .orElseThrow();
        assertThat(failed.isDone()).isTrue();
        assertThat(choose(List.of(TARGETS.get(2), TARGETS.get(1), TARGETS.get(0)), first))
                .isNotEqualTo(preferred);
        assertThat(choose(TARGETS, second)).isEqualTo(preferred);
        assertThat(choose(TARGETS, original)).isEqualTo(preferred);
    }

    @Test
    void explicitStickyTargetTakesPrecedence() throws Exception {
        Request request = request("sticky");
        request.attachments()
                .put(
                        StickyAttachments.STICKY,
                        (_ep, _req, _limits) -> Optional.of(Futures.immediateFuture(
                                new TestResponse().code(200).withHeader("node", "sticky"))));
        assertThat(choose(TARGETS, request)).isEqualTo("sticky");
    }

    private static String choose(List<TargetUri> targets, Request request) throws Exception {
        ConsistentHashingNodeSelectionStrategyChannel strategy =
                new ConsistentHashingNodeSelectionStrategyChannel(targets, channels(targets), (_ep, _req, _lim) -> {
                    throw new AssertionError("Unexpected fallback");
                });
        try (Response response = strategy.maybeExecute(
                        TestEndpoint.GET, request, LimitedChannel.LimitEnforcement.DEFAULT_ENABLED)
                .orElseThrow()
                .get()) {
            return response.getFirstHeader("node").orElseThrow();
        }
    }

    private static List<LimitedChannel> channels(List<TargetUri> targets) {
        return targets.stream()
                .map(ConsistentHashingNodeSelectionStrategyChannelTest::channel)
                .toList();
    }

    private static LimitedChannel channel(TargetUri target) {
        String identity = target.uri()
                + target.resolvedAddress().map(InetAddress::getHostAddress).orElse("");
        return (_ep, _req, _limits) ->
                Optional.of(Futures.immediateFuture(new TestResponse().code(200).withHeader("node", identity)));
    }

    private static Request request(String key) {
        return Request.builder()
                .putAttachment(
                        RoutingKey.ATTACHMENT_KEY, RoutingKey.of(new RoutingKey.Value(RoutingKey.Type.STRING, key)))
                .build();
    }
}
