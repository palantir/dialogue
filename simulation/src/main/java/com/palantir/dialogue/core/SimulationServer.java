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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SimulationServer implements Channel {
    private static final Logger log = LoggerFactory.getLogger(SimulationServer.class);

    private final Simulation simulation;
    private final String metricName;
    private final Meter requestMeter;
    private final Counter activeRequests;
    private final ImmutableList<ServerHandler> handlers;

    private SimulationServer(Builder builder) {
        this.metricName = Preconditions.checkNotNull(builder.metricName, "metricName");
        this.simulation = Preconditions.checkNotNull(builder.simulation, "simulation");
        this.requestMeter = simulation.metrics().meter(String.format("[%s] request", metricName));
        this.activeRequests = simulation.metrics().counter(String.format("[%s] activeRequests", metricName));
        Preconditions.checkState(!builder.handlers.isEmpty(), "Handlers can't be empty");
        this.handlers = ImmutableList.copyOf(builder.handlers);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        activeRequests.inc();
        requestMeter.mark();
        simulation.metrics().report();

        for (ServerHandler handler : handlers) {
            Optional<ListenableFuture<Response>> maybeResp = handler.maybeExecute(this, endpoint, request);
            if (!maybeResp.isPresent()) {
                continue;
            }

            ListenableFuture<Response> resp = maybeResp.get();
            resp.addListener(activeRequests::dec, MoreExecutors.directExecutor());
            Futures.addCallback(
                    resp,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Response result) {
                            log.debug(
                                    "time={} server={} status={} traceid={}",
                                    Duration.ofNanos(simulation.clock().read()),
                                    metricName,
                                    result.code(),
                                    request != null ? request.headerParams().get("X-B3-TraceId") : null);
                        }

                        @Override
                        public void onFailure(Throwable t) {}
                    },
                    MoreExecutors.directExecutor());

            return resp;
        }

        log.error("No handler available for request {}", request);
        activeRequests.dec();
        return Futures.immediateFailedFuture(new RuntimeException("No handler"));
    }

    @Override
    public String toString() {
        return metricName;
    }

    public static class Builder {

        private String metricName;
        private Simulation simulation;
        private ResponseTimeFunction responseTime;
        private Function<Builder, Channel> finalStep = SimulationServer::new;
        private ImmutableList<ServerHandler> handlers = ImmutableList.of();

        Builder metricName(String value) {
            metricName = value;
            return this;
        }

        Builder simulation(Simulation value) {
            simulation = value;
            return this;
        }

        HandlerBuilder0 newHandler() {
            ServerHandler handlerBuilder = new ServerHandler(this);
            handlers = ImmutableList.of(handlerBuilder);
            return handlerBuilder;
        }

        HandlerBuilder1 response(Response response) {
            HandlerBuilder0 builder0 = newHandler();
            HandlerBuilder1 builder1 = builder0.response(response);
            return builder1;
        }

        Channel build() {
            return finalStep.apply(this);
        }

        Builder untilNthRequest(int nthRequest) {
            return until(ComposedSimulationServer.nthRequest(nthRequest));
        }

        Builder untilTime(Duration cutover) {
            return until(ComposedSimulationServer.time(cutover));
        }

        private Builder until(ComposedSimulationServer.SwitchoverPredicate predicate) {
            Channel server1 = build();

            Builder nextBuilder = builder();
            nextBuilder.finalStep = server2Builder -> {
                SimulationServer server2 = new SimulationServer(server2Builder);
                return new ComposedSimulationServer(simulation.clock(), server1, server2, predicate);
            };

            nextBuilder.metricName(metricName);
            nextBuilder.simulation(simulation);
            nextBuilder.responseTime = responseTime;
            nextBuilder.handlers = handlers;
            return nextBuilder;
        }
    }

    /**
     * Declarative server handler, built using a staged-builder. Returns control flow to the original builder at the
     * end.
     */
    public static class ServerHandler implements HandlerBuilder0, HandlerBuilder1 {

        private final SimulationServer.Builder returnBuilder;

        private Predicate<Endpoint> predicate = endpoint -> true;
        private Response response;
        private ResponseTimeFunction responseTimeFunction;

        public ServerHandler(Builder returnBuilder) {
            this.returnBuilder = returnBuilder;
        }

        public Optional<ListenableFuture<Response>> maybeExecute(
                SimulationServer server, Endpoint endpoint, Request _request) {
            if (!predicate.test(endpoint)) {
                return Optional.empty();
            }

            Duration responseTime = responseTimeFunction.getResponseTime(server);
            return Optional.of(
                    server.simulation.schedule(() -> response, responseTime.toNanos(), TimeUnit.NANOSECONDS));
        }

        @Override
        public HandlerBuilder1 response(Response resp) {
            this.response = resp;
            return this;
        }

        @Override
        public SimulationServer.Builder responseTime(ResponseTimeFunction func) {
            this.responseTimeFunction = func;
            return returnBuilder;
        }
    }

    public interface HandlerBuilder0 {
        HandlerBuilder1 response(Response resp);
    }

    public interface HandlerBuilder1 {
        SimulationServer.Builder responseTime(ResponseTimeFunction func);

        /** BEWARE: servers don't actually behave like this. */
        default SimulationServer.Builder responseTimeConstant(Duration duration) {
            return responseTime(server -> duration);
        }

        /**
         * This heuristic delivers the goal 'responseTime' only when the server is under zero load. At a certain
         * number of concurrent requests (the 'capacity'), the response time will double. Above this, the server
         * returns 5x response time to simulate overloading.
         */
        default SimulationServer.Builder responseTimeUpToCapacity(Duration bestCase, int capacity) {
            return responseTime(server -> {
                long expected = bestCase.toNanos();
                long inflight = server.activeRequests.getCount();

                if (inflight > capacity) {
                    return Duration.ofNanos(5 * expected); // above stated 'capacity', server dies a brutal death
                }

                return Duration.ofNanos(expected + (expected * inflight) / capacity);
            });
        }
    }

    interface ResponseTimeFunction {
        Duration getResponseTime(SimulationServer server);
    }
}
