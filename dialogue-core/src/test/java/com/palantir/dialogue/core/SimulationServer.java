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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;

public final class SimulationServer {

    private final Builder builder;

    private SimulationServer(Builder builder) {
        this.builder = builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ListenableFuture<Response> handleRequest(Endpoint endpoint, Request request) {
        // TODO(dfox): use the response time delay!
        return Futures.immediateFuture(builder.response);
    }

    @Override
    public String toString() {
        return "SimulationServer{name=" + builder.name + '}';
    }

    public static class Builder {

        private String name;
        private Response response;
        private Duration responseTime;

        Builder name(String value) {
            name = value;
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
            return new SimulationServer(this);
        }
    }
}
