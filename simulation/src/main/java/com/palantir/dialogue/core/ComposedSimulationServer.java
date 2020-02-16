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
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In order to allow server behavior to change during a simulation, we can just switch between two basic servers at
 * some cutover point.
 */
final class ComposedSimulationServer implements Channel {
    private static final Logger log = LoggerFactory.getLogger(ComposedSimulationServer.class);

    private final Ticker clock;
    private final Channel first;
    private final Channel second;
    private final SwitchoverPredicate predicate;
    private boolean switchedOver = false;

    ComposedSimulationServer(
            Ticker clock, Channel first, Channel second, SwitchoverPredicate predicate) {
        this.clock = clock;
        this.first = first;
        this.second = second;
        this.predicate = predicate;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        boolean switchoverNow = predicate.switchover(clock);
        if (switchoverNow && !switchedOver) {
            switchedOver = true;
            log.info("time={} cutting over from first={} -> second={}", clock.read(), first, second);
        }

        if (!switchoverNow && switchedOver) {
            switchedOver = false;
            log.info("time={} cutting back from second={} -> first={}", clock.read(), second, first);
        }

        Channel server = switchedOver ? second : first;
        return server.execute(endpoint, request);
    }

    @Override
    public String toString() {
        Channel current = switchedOver ? second : first;
        return current.toString();
    }

    // can be stateful!
    interface SwitchoverPredicate {
        boolean switchover(Ticker clock);
    }

    static SwitchoverPredicate time(Duration cutover) {
        return new SwitchoverPredicate() {
            @Override
            public boolean switchover(Ticker clock) {
                return clock.read() >= cutover.toNanos();
            }
        };
    }
}
