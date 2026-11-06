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

import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReason.DueTo;
import com.palantir.conjure.java.api.errors.QosReasons;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TestResponseQosEncoder;
import com.palantir.dialogue.core.Behavior.PermitControl;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class BehaviorTest {

    @ParameterizedTest
    @CsvSource({
        "200, success, http-2xx",
        "204, success, http-2xx",
        "101, success, other",
        "304, success, other",
        "308, success, other",
        "400, success, http-4xx",
        "404, success, http-4xx",
        "429, ignore, http-429",
        "500, ignore, http-500",
        "502, dropped, http-5xx",
        "503, dropped, http-503",
        "504, dropped, http-5xx",
    })
    void hostLevelResponses(int code, String decision, String cause) {
        assertThat(onSuccess(Behavior.HOST_LEVEL, new TestResponse().code(code)))
                .containsExactly(new Decision(decision, cause));
    }

    @Test
    void hostLevelDropsRetryOther() {
        TestResponse retryOther = new TestResponse().code(308).withHeader(HttpHeaders.LOCATION, "https://localhost");
        assertThat(onSuccess(Behavior.HOST_LEVEL, retryOther)).containsExactly(new Decision("dropped", "http-308"));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void hostLevelIgnoresCustomQos(int code) {
        assertThat(onSuccess(Behavior.HOST_LEVEL, customQos(code)))
                .containsExactly(new Decision("ignore", "qos-custom"));
    }

    @Test
    void hostLevelFailures() {
        assertThat(onFailure(Behavior.HOST_LEVEL, new SocketTimeoutException("read timed out")))
                .containsExactly(new Decision("dropped", "SocketTimeoutException"));
        assertThat(onFailure(Behavior.HOST_LEVEL, new IOException("wrapper", new SocketTimeoutException())))
                .as("only the outermost exception is used, as in dialogue.client.request.retry")
                .containsExactly(new Decision("dropped", "IOException"));
        assertThat(onFailure(Behavior.HOST_LEVEL, new CancellationException()))
                .containsExactly(new Decision("ignore", "CancellationException"));
    }

    @ParameterizedTest
    @CsvSource({
        "200, success, http-2xx",
        "404, success, http-4xx",
        "429, dropped, http-429",
        "500, dropped, http-500",
        "503, ignore, http-5xx",
    })
    void endpointLevelResponses(int code, String decision, String cause) {
        assertThat(onSuccess(Behavior.ENDPOINT_LEVEL, new TestResponse().code(code)))
                .containsExactly(new Decision(decision, cause));
    }

    @Test
    void endpointLevelDoesNotDropCustomQos() {
        assertThat(onSuccess(Behavior.ENDPOINT_LEVEL, customQos(429)))
                .containsExactly(new Decision("success", "http-4xx"));
    }

    private static List<Decision> onSuccess(Behavior behavior, Response response) {
        RecordingControl control = new RecordingControl();
        behavior.onSuccess(response, control);
        return control.decisions;
    }

    private static List<Decision> onFailure(Behavior behavior, Throwable throwable) {
        RecordingControl control = new RecordingControl();
        behavior.onFailure(throwable, control);
        return control.decisions;
    }

    private static TestResponse customQos(int code) {
        TestResponse response = new TestResponse().code(code);
        QosReasons.encodeToResponse(
                QosReason.builder().reason("reason").dueTo(DueTo.CUSTOM).build(),
                response,
                TestResponseQosEncoder.INSTANCE);
        return response;
    }

    record Decision(String decision, String cause) {}

    private static final class RecordingControl implements PermitControl {
        private final List<Decision> decisions = new ArrayList<>();

        @Override
        public void ignore(String cause) {
            decisions.add(new Decision("ignore", cause));
        }

        @Override
        public void dropped(String cause) {
            decisions.add(new Decision("dropped", cause));
        }

        @Override
        public void success(String cause) {
            decisions.add(new Decision("success", cause));
        }
    }
}
