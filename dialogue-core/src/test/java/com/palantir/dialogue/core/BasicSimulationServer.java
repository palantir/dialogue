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

import com.codahale.metrics.Meter;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class BasicSimulationServer implements SimulationServer {
    private final String metricName;
    private final SimulatedScheduler simulation;
    private final Response response;
    private final Duration responseTime;
    private final Meter requestRate;

    private BasicSimulationServer(Builder builder) {
        this.metricName = Preconditions.checkNotNull(builder.metricName);
        this.simulation = Preconditions.checkNotNull(builder.simulation);
        this.response = Preconditions.checkNotNull(builder.response);
        this.responseTime = Preconditions.checkNotNull(builder.responseTime);
        this.requestRate = builder.simulation.metrics().meter(builder.metricName);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ListenableScheduledFuture<Response> handleRequest(Endpoint _endpoint, Request _request) {
        requestRate.mark();
        return simulation.schedule(
                () -> {
                    System.out.println("time="
                            + simulation.clock().read()
                            + " server="
                            + metricName
                            + " status="
                            + response.code()
                            + " duration="
                            + responseTime);
                    return response;
                },
                responseTime.toNanos(),
                TimeUnit.NANOSECONDS);
    }

    @Override
    public String toString() {
        return "SimulationServer{name=" + metricName + '}';
    }

    public static class Builder {

        private String metricName;
        private SimulatedScheduler simulation;
        private Response response;
        private Duration responseTime;
        private Function<Builder, SimulationServer> finalStep = BasicSimulationServer::new;

        Builder metricName(String value) {
            metricName = value;
            return this;
        }

        Builder simulation(SimulatedScheduler value) {
            simulation = value;
            return this;
        }

        /** What response should we return. */
        Builder response(Response value) {
            response = value;
            return this;
        }

        /** How long should responses take. */
        Builder responseTime(Duration value) {
            responseTime = value;
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
            nextBuilder.responseTime(responseTime);
            nextBuilder.response(response);
            return nextBuilder;
        }
    }
}
