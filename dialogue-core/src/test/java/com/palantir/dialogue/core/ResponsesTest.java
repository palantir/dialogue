/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.conjure.java.api.orca.OrcaLoadReport;
import com.palantir.conjure.java.api.orca.OrcaLoadReports;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import org.junit.jupiter.api.Test;

class ResponsesTest {

    // OrcaLoadReports owns this header name internally; duplicated here only to write a raw malformed value.
    private static final String LOAD_METRICS_HEADER = "endpoint-load-metrics";

    @Test
    void proxyUpstreamRequestAttempts_missing() {
        try (Response response = new TestResponse()) {
            assertThat(Responses.getProxyUpstreamRequestAttempts(response)).isZero();
        }
    }

    @Test
    void proxyUpstreamRequestAttempts_malformed() {
        try (Response response = new TestResponse().withHeader(Responses.PROXY_UPSTREAM_REQUEST_ATTEMPTS, "deadbeef")) {
            assertThat(Responses.getProxyUpstreamRequestAttempts(response)).isZero();
        }
    }

    @Test
    void proxyUpstreamRequestAttempts_valid() {
        try (Response response = new TestResponse().withHeader(Responses.PROXY_UPSTREAM_REQUEST_ATTEMPTS, "1")) {
            assertThat(Responses.getProxyUpstreamRequestAttempts(response)).isOne();
        }
    }

    @Test
    void proxyUpstreamRequestAttempts_trimmed() {
        try (Response response = new TestResponse().withHeader(Responses.PROXY_UPSTREAM_REQUEST_ATTEMPTS, " 2 ")) {
            assertThat(Responses.getProxyUpstreamRequestAttempts(response)).isEqualTo(2);
        }
    }

    @Test
    void proxyUpstreamRequestAttempts_valid_default() {
        try (Response response = new TestResponse().withHeader(Responses.PROXY_UPSTREAM_REQUEST_ATTEMPTS, "0")) {
            assertThat(Responses.getProxyUpstreamRequestAttempts(response)).isZero();
        }
    }

    @Test
    void proxyUpstreamRequestAttempts_negative() {
        try (Response response = new TestResponse().withHeader(Responses.PROXY_UPSTREAM_REQUEST_ATTEMPTS, "-1")) {
            assertThat(Responses.getProxyUpstreamRequestAttempts(response)).isZero();
        }
    }

    @Test
    void utilization_missing() {
        try (Response response = new TestResponse()) {
            assertThat(Responses.parseUtilization(response)).isEmpty();
        }
    }

    @Test
    void utilization_prefersApplicationOverCpu() {
        try (Response response = responseWith(OrcaLoadReport.builder()
                .applicationUtilization(0.7)
                .cpuUtilization(0.2)
                .build())) {
            assertThat(Responses.parseUtilization(response)).hasValue(0.7);
        }
    }

    @Test
    void utilization_fallsBackToCpu() {
        try (Response response =
                responseWith(OrcaLoadReport.builder().cpuUtilization(0.42).build())) {
            assertThat(Responses.parseUtilization(response)).hasValue(0.42);
        }
    }

    @Test
    void utilization_mayExceedOne() {
        try (Response response = responseWith(
                OrcaLoadReport.builder().applicationUtilization(1.5).build())) {
            assertThat(Responses.parseUtilization(response)).hasValue(1.5);
        }
    }

    @Test
    void utilization_garbage() {
        try (Response response = new TestResponse().withHeader(LOAD_METRICS_HEADER, "not a valid report")) {
            assertThat(Responses.parseUtilization(response)).isEmpty();
        }
    }

    private static TestResponse responseWith(OrcaLoadReport report) {
        TestResponse response = new TestResponse().code(200);
        OrcaLoadReports.encodeToResponse(report, response, (resp, name, value) -> resp.withHeader(name, value));
        return response;
    }
}
