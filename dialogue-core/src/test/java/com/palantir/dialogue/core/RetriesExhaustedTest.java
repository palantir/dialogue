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

import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class RetriesExhaustedTest {
    @Test
    void markingTwicePreservesExistingDiagnostics() {
        IOException first = new IOException("First failure");
        RuntimeException diagnostic = new RuntimeException("Existing diagnostic");
        first.addSuppressed(diagnostic);

        RetriesExhausted.addDiagnostic(first);
        Throwable exhaustionDiagnostic = first.getSuppressed()[1];
        RetriesExhausted.addDiagnostic(first);

        assertThat(first.getSuppressed()).containsExactly(diagnostic, exhaustionDiagnostic);
        assertThat(exhaustionDiagnostic.getStackTrace()).isEmpty();
        assertThat(exhaustionDiagnostic).isInstanceOfSatisfying(SafeLoggable.class, loggable -> {
            assertThat(loggable.getLogMessage()).isEqualTo("Dialogue retries exhausted");
            assertThat(loggable.getArgs()).containsExactly(SafeArg.of(Responses.RETRIES_EXHAUSTED, "true"));
        });
    }

    @Test
    void addsHeaderAndPreservesResponseContentsAndLifecycle() {
        TestResponse delegate = TestResponse.withBody("unavailable").code(503).withHeader("Other-Header", "value");

        Response response = RetriesExhausted.withHeader(delegate);

        assertThat(response.code()).isEqualTo(503);
        assertThat(response.body()).isSameAs(delegate.body());
        assertThat(response.attachments()).isSameAs(delegate.attachments());
        assertThat(response.getFirstHeader("dialogue-retries-exhausted")).hasValue("true");
        assertThat(response.getFirstHeader("Other-Header")).hasValue("value");
        assertThat(response.headers().get("dialogue-retries-exhausted")).containsExactly("true");
        assertThat(response.headers().get("other-header")).containsExactly("value");
        assertThat(Responses.hasRetriesExhaustedHeader(delegate)).isFalse();
        assertThat(delegate.isClosed()).isFalse();

        response.close();

        assertThat(delegate.isClosed()).isTrue();
    }

    @Test
    void preservesResponseAlreadyMarkedExhausted() {
        TestResponse response = new TestResponse().code(503).withHeader(Responses.RETRIES_EXHAUSTED, "TrUe");

        assertThat(RetriesExhausted.withHeader(response)).isSameAs(response);
        assertThat(Responses.hasRetriesExhaustedHeader(response)).isTrue();
    }
}
