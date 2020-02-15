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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BasicSimulationServer implements SimulationServer {
    private static final Logger log = LoggerFactory.getLogger(BasicSimulationServer.class);

    private final String metricName;
    private final Simulation simulation;
    private final Response response;
    private final Meter requestMeter;
    private final Counter activeRequests;
    private final Function<BasicSimulationServer, Duration> responseTime;

    private BasicSimulationServer(Builder builder) {
        this.metricName = Preconditions.checkNotNull(builder.metricName, "metricName");
        this.simulation = Preconditions.checkNotNull(builder.simulation, "simulation");
        this.response = Preconditions.checkNotNull(builder.response, "response");
        this.responseTime = Preconditions.checkNotNull(builder.responseTime, "responseTime");
        this.requestMeter = simulation.metrics().meter(String.format("[%s] request", metricName));
        this.activeRequests = simulation.metrics().counter(String.format("[%s] activeRequests", metricName));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ListenableScheduledFuture<Response> execute(Endpoint _endpoint, Request request) {
        activeRequests.inc();
        requestMeter.mark();
        Duration duration = responseTime.apply(this);
        return simulation.schedule(
                () -> {
                    log.debug(
                            "time={} server={} status={} duration={} traceid={}",
                            simulation.clock().read(),
                            metricName,
                            response.code(),
                            duration,
                            request != null ? request.headerParams().get("X-B3-TraceId") : null);
                    activeRequests.dec();
                    return response;
                },
                duration.toNanos(),
                TimeUnit.NANOSECONDS);
    }

    @Override
    public String toString() {
        return metricName;
    }

    public static class Builder {

        private String metricName;
        private Simulation simulation;
        private Response response;
        private Function<BasicSimulationServer, Duration> responseTime;
        private Function<Builder, SimulationServer> finalStep = BasicSimulationServer::new;

        Builder metricName(String value) {
            metricName = value;
            return this;
        }

        Builder simulation(Simulation value) {
            simulation = value;
            return this;
        }

        /** What response should we return. */
        Builder response(Response value) {
            response = value;
            return this;
        }

        /** BEWARE: servers don't actually behave like this! */
        Builder responseTimeConstant(Duration duration) {
            responseTime = server -> duration;
            return this;
        }

        /**
         * This heuristic delivers the goal 'responseTime' only when the server is under zero load. At a certain
         * number of concurrent requests (the 'capacity'), the response time will double. Above this, the server
         * returns 5x response time to simulate overloading.
         */
        Builder responseTimeUpToCapacity(Duration bestCase, int capacity) {
            responseTime = server -> {
                long expected = bestCase.toNanos();
                long inflight = server.activeRequests.getCount();

                if (inflight > capacity) {
                    return Duration.ofNanos(5 * expected); // above stated 'capacity', server dies a brutal death
                }

                return Duration.ofNanos(expected + (expected * inflight) / capacity);
            };
            return this;
        }

        SimulationServer build() {
            return finalStep.apply(this);
        }

        Builder untilNthRequest(int nthRequest) {
            return until(ComposedSimulationServer.nthRequest(nthRequest));
        }

        Builder untilTime(Duration cutover) {
            return until(ComposedSimulationServer.time(cutover));
        }

        private Builder until(ComposedSimulationServer.SwitchoverPredicate predicate) {
            SimulationServer server1 = build();

            Builder nextBuilder = builder();
            nextBuilder.finalStep = server2Builder -> {
                BasicSimulationServer server2 = new BasicSimulationServer(server2Builder);
                return new ComposedSimulationServer(simulation.clock(), server1, server2, predicate);
            };

            nextBuilder.metricName(metricName);
            nextBuilder.simulation(simulation);
            nextBuilder.responseTime = responseTime;
            nextBuilder.response(response);
            return nextBuilder;
        }
    }
}
