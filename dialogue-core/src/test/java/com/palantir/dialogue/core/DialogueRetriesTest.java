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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.palantir.dialogue.RetriesExhaustedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class DialogueRetriesTest {
    @Test
    void markingTwicePreservesDiagnosticsAndReusesSharedMarker() {
        IOException first = new IOException("First failure");
        IOException second = new IOException("Second failure");
        RuntimeException diagnostic = new RuntimeException("Existing diagnostic");
        first.addSuppressed(diagnostic);

        DialogueRetries.setRetriesExhausted(first);
        DialogueRetries.setRetriesExhausted(first);
        DialogueRetries.setRetriesExhausted(second);

        assertThat(first.getSuppressed()).containsExactly(diagnostic, RetriesExhaustedException.INSTANCE);
        assertThat(second.getSuppressed()).containsExactly(RetriesExhaustedException.INSTANCE);
        assertThat(DialogueRetries.isRetriesExhausted(first)).isTrue();
        assertThat(DialogueRetries.isRetriesExhausted(second)).isTrue();
    }

    @Test
    void detectsMarkerOnCauseWithoutAddingAnother() {
        IOException original = new IOException("Original failure");
        DialogueRetries.setRetriesExhausted(original);
        RuntimeException wrapper = new RuntimeException(new UncheckedIOException(original));

        assertThat(DialogueRetries.isRetriesExhausted(wrapper)).isTrue();
        DialogueRetries.setRetriesExhausted(wrapper);
        assertThat(wrapper.getSuppressed()).isEmpty();
        assertThat(original.getSuppressed()).containsExactly(RetriesExhaustedException.INSTANCE);
    }

    @Test
    void limitsCauseTraversalToOneHundredExceptions() {
        Throwable failure = new IOException("Original failure");
        DialogueRetries.setRetriesExhausted(failure);
        for (int chainLength = 1; chainLength < 100; chainLength++) {
            failure = new IOException("Wrapper", failure);
        }

        assertThat(DialogueRetries.isRetriesExhausted(failure))
                .as("Marker on the 100th exception is within the limit")
                .isTrue();
        assertThat(DialogueRetries.isRetriesExhausted(new IOException("Wrapper", failure)))
                .as("Marker on the 101st exception is beyond the limit")
                .isFalse();
    }

    @Test
    void ignoresUnrelatedSuppressedExceptionSubtrees() {
        IOException suppressedFailure = new IOException("Suppressed failure");
        DialogueRetries.setRetriesExhausted(suppressedFailure);
        RuntimeException failure = new UncheckedIOException(new IOException("Unmarked cause"));
        failure.addSuppressed(suppressedFailure);

        assertThat(DialogueRetries.isRetriesExhausted(failure)).isFalse();
    }

    @Test
    void handlesCauseCycleWithAndWithoutMarker() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            IOException first = new IOException("First failure");
            IOException second = new IOException("Second failure");
            first.initCause(second);
            second.initCause(first);

            assertThat(DialogueRetries.isRetriesExhausted(first)).isFalse();
            DialogueRetries.setRetriesExhausted(second);
            assertThat(DialogueRetries.isRetriesExhausted(first)).isTrue();
        });
    }
}
