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

import com.google.common.util.concurrent.AtomicDouble;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.RoutingAttachments.RoutingKey;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import com.palantir.dialogue.core.FairQueuedChannel.QueueKey;
import com.palantir.dialogue.core.QueueExecutor.DeferredCall;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.immutables.value.Value;

@SuppressWarnings({"StrictUnusedVariable", "UnusedMethod", "FinalClass"})
final class DrfQueuedChannel {

    private final Map<RoutingKey, RoutingKeyState> allQueues = new HashMap<>();

    private static class HostState {

        private final CautiousIncreaseAggressiveDecreaseConcurrencyLimiter hostLimiter;
        private final Map<Object, CautiousIncreaseAggressiveDecreaseConcurrencyLimiter> endpoints;

        private HostState() {
            hostLimiter = ConcurrencyLimitedChannel.createLimiter(Behavior.HOST_LEVEL);
            endpoints = new HashMap<>();
        }

        public int capacity(Endpoint endpoint) {
            return hostLimiter.currentAvailable();
        }

        /**
         * Constant {@link Endpoint endpoints} may be safely used as cache keys, as opposed to dynamically created
         * {@link Endpoint} objects which would result in a memory leak.
         */
        private static boolean isConstant(Endpoint endpoint) {
            // The conjure generator creates endpoints as enum values, which can safely be cached because they aren't
            // dynamically created.
            return endpoint instanceof Enum;
        }

        /**
         * Creates a cache key for the given endpoint. Some consumers (CJR feign shim) may not use endpoint enums, so we
         * cannot safely hold references to potentially short-lived objects. In such cases we use a string value based on
         * the service-name endpoint-name tuple.
         */
        private static Object key(Endpoint endpoint) {
            return isConstant(endpoint) ? endpoint : stringKey(endpoint);
        }

        private static String stringKey(Endpoint endpoint) {
            return endpoint.serviceName() + '.' + endpoint.endpointName();
        }
    }

    // Represents a single routing key, and tracks current usage of resources as well as DeferredCalls.
    private static class RoutingKeyState {

        private final RoutingKey _routingKey;
        private final Map<HostId, Queue<DeferredCall>> queuedRequests = new HashMap<>();
        private final Map<HostEndpoint, Integer> inFlight = new HashMap<>();

        private RoutingKeyState(RoutingKey routingKey) {
            this._routingKey = routingKey;
        }

        void enqueue(HostId hostId, DeferredCall deferredCall) {
            Queue<DeferredCall> deferredCalls = queuedRequests.get(hostId);
            if (deferredCalls == null) {
                deferredCalls = new ArrayDeque<>();
            }
            deferredCalls.add(deferredCall);
        }

        @SuppressWarnings("NullAway")
        double dominantShare(Map<HostId, HostState> hosts) {
            AtomicDouble dominantShare = new AtomicDouble(0);
            inFlight.forEach((hostEndpoint, currentInFlight) -> {
                dominantShare.set(Math.max(
                        dominantShare.get(),
                        (double) currentInFlight
                                / hosts.get(hostEndpoint.hostId()).capacity(hostEndpoint.endpoint())));
            });
            return dominantShare.get();
        }

        void complete(HostId hostId, Endpoint endpoint) {
            inFlight.get(ImmutableHostEndpoint.of(hostId, endpoint));
        }
    }

    void enqueue(QueueKey queueKey, QueueExecutor.DeferredCall call) {
        routingKeyState(queueKey.routingKey()).enqueue(queueKey.hostKey(), call);
    }

    void complete(QueueKey queueKey, Endpoint endpoint) {
        routingKeyState(queueKey.routingKey()).complete(queueKey.hostKey(), endpoint);
    }

    int dispatch() {
        return 0;
    }

    private RoutingKeyState routingKeyState(RoutingKey routingKey) {
        RoutingKeyState routingKeyState = allQueues.get(routingKey);
        if (routingKeyState == null) {
            routingKeyState = new RoutingKeyState(routingKey);
        }
        return routingKeyState;
    }

    @Value.Immutable
    interface HostEndpoint {
        @Value.Parameter
        HostId hostId();

        // Endpoint is funky
        @Value.Parameter
        Endpoint endpoint();
    }
}
