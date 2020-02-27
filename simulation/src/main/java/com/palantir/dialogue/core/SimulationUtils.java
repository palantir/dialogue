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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SimulationUtils {
    private static final Logger log = LoggerFactory.getLogger(SimulationUtils.class);

    public static Response response(int status, String version) {
        return new Response() {
            @Override
            public InputStream body() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public int code() {
                return status;
            }

            @Override
            public Map<String, List<String>> headers() {
                if (version == null) {
                    return ImmutableMap.of();
                }
                return ImmutableMap.of("server", ImmutableList.of("foundry-catalog/" + version));
            }

            @Override
            public void close() {}
        };
    }

    public static Response wrapWithCloseInstrumentation(Response delegate, TaggedMetricRegistry registry) {
        return new Response() {
            private int timesClosed = 0;

            @Override
            public InputStream body() {
                return delegate.body();
            }

            @Override
            public int code() {
                return delegate.code();
            }

            @Override
            public Map<String, List<String>> headers() {
                return delegate.headers();
            }

            @Override
            public void close() {
                if (timesClosed == 0) {
                    MetricNames.responseClose(registry).inc();
                    timesClosed += 1;
                } else {
                    log.warn(
                            "Duplicate close, already called {} time(s)",
                            timesClosed,
                            new SafeRuntimeException("for stacktrace"));
                }
            }
        };
    }

    static final String SERVICE_NAME = "svc";

    public static Endpoint endpoint(String name) {
        return new Endpoint() {
            @Override
            public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

            @Override
            public HttpMethod httpMethod() {
                return HttpMethod.GET;
            }

            @Override
            public String serviceName() {
                return SERVICE_NAME;
            }

            @Override
            public String endpointName() {
                return name;
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public String toString() {
                return endpointName();
            }
        };
    }

    private SimulationUtils() {}
}
