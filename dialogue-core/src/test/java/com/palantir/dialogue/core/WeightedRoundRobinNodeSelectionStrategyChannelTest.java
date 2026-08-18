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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class WeightedRoundRobinNodeSelectionStrategyChannelTest {

    private final Random random = new Random(9821349871234L);
    private final Request request = Request.builder().build();
    private final Endpoint endpoint = TestEndpoint.GET;

    private long nowNanos = 0;
    private final Ticker clock = () -> nowNanos;

    @Test
    void routes_away_from_the_saturated_node_toward_the_idle_one() {
        CountingChannel idle = new CountingChannel(() -> respond(200, 0.1));
        CountingChannel busy = new CountingChannel(() -> respond(200, 0.9));
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(idle, busy);
        warmUpPastBlackout(channel, idle, busy);

        send(channel, 2000);

        assertThat(idle.count)
                .as("idle node (util 0.1) should receive far more traffic than the busy node (util 0.9)")
                .isGreaterThan(busy.count * 2);
        assertThat(busy.count)
                .as("but the busy node is still probed, not starved")
                .isGreaterThan(100);
    }

    @Test
    void distributes_roughly_evenly_when_utilization_is_equal() {
        CountingChannel first = new CountingChannel(() -> respond(200, 0.5));
        CountingChannel second = new CountingChannel(() -> respond(200, 0.5));
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(first, second);
        warmUpPastBlackout(channel, first, second);

        send(channel, 2000);

        assertThat(first.count).isBetween(800, 1200);
        assertThat(second.count).isBetween(800, 1200);
    }

    @Test
    void distributes_roughly_evenly_before_any_utilization_is_reported() {
        CountingChannel first = new CountingChannel(() -> Optional.of(Futures.immediateFuture(new TestResponse())));
        CountingChannel second = new CountingChannel(() -> Optional.of(Futures.immediateFuture(new TestResponse())));
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(first, second);

        send(channel, 2000);

        assertThat(first.count).isBetween(800, 1200);
        assertThat(second.count).isBetween(800, 1200);
    }

    @Test
    void distributes_roughly_evenly_during_blackout() {
        CountingChannel idle = new CountingChannel(() -> respond(200, 0.1));
        CountingChannel busy = new CountingChannel(() -> respond(200, 0.9));
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(idle, busy);

        send(channel, 2000);

        assertThat(idle.count).isBetween(800, 1200);
        assertThat(busy.count).isBetween(800, 1200);
    }

    @Test
    void distributes_roughly_evenly_when_only_one_node_has_a_trusted_reading() {
        CountingChannel reporting = new CountingChannel(() -> respond(200, 0.9));
        CountingChannel unknown = new CountingChannel(() -> Optional.of(Futures.immediateFuture(new TestResponse())));
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(reporting, unknown);
        warmUpPastBlackout(channel, reporting, unknown);

        send(channel, 2000);

        assertThat(reporting.count).isBetween(800, 1200);
        assertThat(unknown.count).isBetween(800, 1200);
    }

    @Test
    void tries_every_host_then_gives_up_when_all_refuse() {
        CountingChannel first = new CountingChannel(Optional::empty);
        CountingChannel second = new CountingChannel(Optional::empty);
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(first, second);

        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                .isEmpty();
        assertThat(first.count).isEqualTo(1);
        assertThat(second.count).isEqualTo(1);
    }

    @Test
    void re_probes_a_previously_saturated_node_once_its_reading_goes_stale() {
        CountingChannel idle = new CountingChannel(() -> respond(200, 0.1));
        CountingChannel wasSaturated = new CountingChannel(new Supplier<>() {
            private boolean reported = false;

            @Override
            public Optional<ListenableFuture<Response>> get() {
                if (reported) {
                    return Optional.of(Futures.immediateFuture(new TestResponse().code(200)));
                }
                reported = true;
                return respond(200, 5.0);
            }
        });
        WeightedRoundRobinNodeSelectionStrategyChannel channel = channel(idle, wasSaturated);
        warmUpPastBlackout(channel, idle, wasSaturated);

        send(channel, 200);
        int whileFresh = wasSaturated.count;

        // Advance past the expiry so the stale 5.0 reading is dropped; the node should then be re-probed rather than
        // avoided forever (and it reverts toward the peer average, not a fixed neutral).
        nowNanos = WeightedRoundRobinNodeSelectionStrategyChannel.UTILIZATION_EXPIRY_NANOS
                + WeightedRoundRobinNodeSelectionStrategyChannel.BLACKOUT_NANOS
                + 2;
        wasSaturated.count = 0;
        send(channel, 200);
        int afterExpiry = wasSaturated.count;

        assertThat(afterExpiry)
                .as("once its stale high-utilization reading expires, the node is re-probed, not avoided forever")
                .isGreaterThan(whileFresh * 2);
    }

    private WeightedRoundRobinNodeSelectionStrategyChannel channel(LimitedChannel... delegates) {
        return new WeightedRoundRobinNodeSelectionStrategyChannel(
                ImmutableList.copyOf(delegates), random, clock, new DefaultTaggedMetricRegistry(), "channelName");
    }

    /** Records an initial reading from every node, then advances past the A58 blackout so weights take effect. */
    private void warmUpPastBlackout(WeightedRoundRobinNodeSelectionStrategyChannel channel, CountingChannel... nodes) {
        send(channel, 50);
        nowNanos += WeightedRoundRobinNodeSelectionStrategyChannel.BLACKOUT_NANOS + 1;
        for (CountingChannel node : nodes) {
            node.count = 0;
        }
    }

    private void send(WeightedRoundRobinNodeSelectionStrategyChannel channel, int count) {
        for (int i = 0; i < count; i++) {
            channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
        }
    }

    private static Optional<ListenableFuture<Response>> respond(int code, double applicationUtilization) {
        TestResponse response = new TestResponse()
                .code(code)
                .withHeader(Responses.UTILIZATION_HEADER, Double.toString(applicationUtilization));
        return Optional.of(Futures.immediateFuture(response));
    }

    private static final class CountingChannel implements LimitedChannel {
        private final Supplier<Optional<ListenableFuture<Response>>> responder;
        private int count = 0;

        CountingChannel(Supplier<Optional<ListenableFuture<Response>>> responder) {
            this.responder = responder;
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint _endpoint, Request _request, LimitEnforcement _limitEnforcement) {
            count++;
            return responder.get();
        }
    }
}
