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
import com.palantir.logsafe.exceptions.SafeRuntimeException;
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
    private final Counter globalActiveRequests;
    private final ImmutableList<ServerHandler> handlers;
    private long cumulativeServerTimeNanos = 0;

    private SimulationServer(Builder builder) {
        this.metricName = Preconditions.checkNotNull(builder.metricName, "metricName");
        this.simulation = Preconditions.checkNotNull(builder.simulation, "simulation");
        this.globalActiveRequests = simulation.metrics().counter(String.format("[%s] activeRequests", metricName));
        Preconditions.checkState(!builder.handlers.isEmpty(), "Handlers can't be empty");
        this.handlers = ImmutableList.copyOf(builder.handlers);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Meter perEndpointRequests =
                simulation.metrics().meter(String.format("[%s] [%s] request", metricName, endpoint.endpointName()));

        globalActiveRequests.inc();
        perEndpointRequests.mark();
        simulation.metrics().report();

        for (ServerHandler handler : handlers) {
            long beforeNanos = simulation.clock().read();
            Optional<ListenableFuture<Response>> maybeResp = handler.maybeExecute(this, endpoint, request);
            if (!maybeResp.isPresent()) {
                continue;
            }

            ListenableFuture<Response> resp = maybeResp.get();
            resp.addListener(
                    () -> {
                        globalActiveRequests.dec();
                        cumulativeServerTimeNanos += simulation.clock().read() - beforeNanos;
                    },
                    MoreExecutors.directExecutor());
            Futures.addCallback(
                    resp,
                    new FutureCallback<Response>() {
                        @Override
                        public void onSuccess(Response result) {
                            log.debug(
                                    "time={} server={} status={} id={}",
                                    Duration.ofNanos(simulation.clock().read()),
                                    metricName,
                                    result.code(),
                                    request != null ? request.headerParams().get(Benchmark.REQUEST_ID_HEADER) : null);
                        }

                        @Override
                        public void onFailure(Throwable _throwable) {}
                    },
                    MoreExecutors.directExecutor());

            return resp;
        }

        log.error("No handler available for request {}", request);
        globalActiveRequests.dec();
        return Futures.immediateFailedFuture(new SafeRuntimeException("No handler"));
    }

    @Override
    public String toString() {
        return metricName;
    }

    // note this is misleading for the black_hole case, because it only increases when a task _returns_
    public Duration getCumulativeServerTime() {
        return Duration.ofNanos(cumulativeServerTimeNanos);
    }

    public static class Builder {
        private String metricName;
        private Simulation simulation;
        private ImmutableList<ServerHandler> handlers = ImmutableList.of();

        Builder metricName(String value) {
            metricName = value;
            return this;
        }

        Builder simulation(Simulation value) {
            simulation = value;
            return this;
        }

        Builder handler(Function<HandlerBuilder0, ServerHandler> configureFunc) {
            handlers = ImmutableList.<ServerHandler>builder()
                    .addAll(handlers)
                    .add(configureFunc.apply(new ServerHandler()))
                    .build();
            return this;
        }

        Builder handler(Endpoint endpoint, Function<HandlerBuilder0, ServerHandler> configureFunc) {
            return handler(h -> {
                HandlerBuilder0 builder = h.endpoint(endpoint);
                return configureFunc.apply(builder);
            });
        }

        Builder until(Duration cutover) {
            return until(cutover, null);
        }

        Builder until(Duration cutover, String message) {
            long cutoverNanos = cutover.toNanos();

            for (ServerHandler handler : handlers) {
                Predicate<Endpoint> existingPredicate = handler.predicate;
                boolean[] switched = {false};
                handler.predicate = endpoint -> {
                    // we just add in this sneaky little precondition to all the existing handlers!
                    if (simulation.clock().read() >= cutoverNanos) {
                        if (message != null && !switched[0]) {
                            simulation.events().event(message);
                            switched[0] = true;
                        }
                        return false;
                    }

                    return existingPredicate.test(endpoint);
                };
            }

            return this;
        }

        SimulationServer build() {
            return new SimulationServer(this);
        }
    }

    /** Declarative server handler, built using a staged-builder. */
    public static class ServerHandler implements HandlerBuilder0, HandlerBuilder1 {

        private Predicate<Endpoint> predicate = endpoint -> true;
        private Function<SimulationServer, Response> responseFunction;
        private ResponseTimeFunction responseTimeFunction;

        public Optional<ListenableFuture<Response>> maybeExecute(
                SimulationServer server, Endpoint endpoint, Request _request) {
            if (predicate != null && !predicate.test(endpoint)) {
                return Optional.empty();
            }

            Duration responseTime = responseTimeFunction.getResponseTime(server);
            return Optional.of(server.simulation.schedule(
                    () -> responseFunction.apply(server), responseTime.toNanos(), TimeUnit.NANOSECONDS));
        }

        @Override
        public HandlerBuilder0 endpoint(Endpoint endpoint) {
            predicate = e -> e.equals(endpoint);
            return this;
        }

        @Override
        public HandlerBuilder1 response(Function<SimulationServer, Response> func) {
            this.responseFunction = func;
            return this;
        }

        @Override
        public ServerHandler responseTime(ResponseTimeFunction func) {
            this.responseTimeFunction = func;
            return this;
        }
    }

    public interface HandlerBuilder0 {
        HandlerBuilder0 endpoint(Endpoint endpoint);

        HandlerBuilder1 response(Function<SimulationServer, Response> func);

        default HandlerBuilder1 response(Response resp) {
            return response(unused -> resp);
        }

        default HandlerBuilder1 response(int status) {
            return response(SimulationUtils.response(status, "1.0.0"));
        }

        default HandlerBuilder1 respond200UntilCapacity(int errorStatus, int capacity) {
            return response(server -> {
                if (server.globalActiveRequests.getCount() > capacity) {
                    return SimulationUtils.response(errorStatus, "1.0.0");
                } else {
                    return SimulationUtils.response(200, "1.0.0");
                }
            });
        }
    }

    public interface HandlerBuilder1 {
        ServerHandler responseTime(ResponseTimeFunction func);

        /** BEWARE: servers don't actually behave like this. */
        default ServerHandler responseTime(Duration duration) {
            return responseTime(server -> duration);
        }

        /**
         * This heuristic delivers the goal 'responseTime' only when the server is under zero load. At a certain
         * number of concurrent requests (the 'capacity'), the response time will double. Above this, the server
         * returns 5x response time to simulate overloading.
         */
        default ServerHandler linearResponseTime(Duration bestCase, int capacity) {
            return responseTime(server -> {
                long expected = bestCase.toNanos();
                long inflight = server.globalActiveRequests.getCount();

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
