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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class DialogueRetriesTest {
    @Test
    void sharesMarkerWithoutReplacingTheFailure() {
        IOException first = new IOException("First failure");
        IOException second = new IOException("Second failure");

        DialogueRetries.setRetriesExhausted(first);
        DialogueRetries.setRetriesExhausted(second);

        assertThat(first.getSuppressed()).containsExactly(RetriesExhaustedException.INSTANCE);
        assertThat(second.getSuppressed()).containsExactly(RetriesExhaustedException.INSTANCE);
        assertThat(DialogueRetries.isRetriesExhausted(first)).isTrue();
        assertThat(DialogueRetries.isRetriesExhausted(second)).isTrue();
    }

    @Test
    void markerHasNoMutableExceptionState() {
        RetriesExhaustedException marker = RetriesExhaustedException.INSTANCE;
        marker.fillInStackTrace();
        marker.setStackTrace(new StackTraceElement[] {new StackTraceElement("Test", "method", "Test.java", 1)});
        marker.addSuppressed(new IOException("Unrelated failure"));

        assertThat(marker.getStackTrace()).isEmpty();
        assertThat(marker.getSuppressed()).isEmpty();
        assertThat(marker.getCause()).isNull();
        assertThatThrownBy(() -> marker.initCause(new IOException("Cause"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markingTwicePreservesExistingDiagnosticsAndAddsOnlyOneMarker() {
        IOException failure = new IOException("Failure");
        RuntimeException diagnostic = new RuntimeException("Existing diagnostic");
        failure.addSuppressed(diagnostic);

        DialogueRetries.setRetriesExhausted(failure);
        DialogueRetries.setRetriesExhausted(failure);

        assertThat(failure.getSuppressed()).containsExactly(diagnostic, RetriesExhaustedException.INSTANCE);
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
    void detectsAbsenceAndMarkersThroughoutCauseCycles() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int prefixLength = 0; prefixLength < 4; prefixLength++) {
                for (int cycleLength = 2; cycleLength < 5; cycleLength++) {
                    assertCauseCycle(prefixLength, cycleLength);
                }
            }
        });
    }

    @Test
    void handlesSelfReferencingCause() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Throwable failure = new SelfCausedException();
            assertThat(DialogueRetries.isRetriesExhausted(failure)).isFalse();
            DialogueRetries.setRetriesExhausted(failure);
            assertThat(DialogueRetries.isRetriesExhausted(failure)).isTrue();
        });
    }

    @Test
    void suppressionDisabledCarrierCannotRetainMarker() {
        Throwable failure = new SuppressionDisabledException();

        DialogueRetries.setRetriesExhausted(failure);

        assertThat(failure.getSuppressed()).isEmpty();
        assertThat(DialogueRetries.isRetriesExhausted(failure)).isFalse();
    }

    private static void assertCauseCycle(int prefixLength, int cycleLength) {
        int size = prefixLength + cycleLength;
        for (int markedIndex = -1; markedIndex < size; markedIndex++) {
            Throwable[] causes = new Throwable[size];
            for (int index = 0; index < size; index++) {
                causes[index] = new IOException("Failure " + index);
            }
            for (int index = 0; index < size - 1; index++) {
                causes[index].initCause(causes[index + 1]);
            }
            causes[size - 1].initCause(causes[prefixLength]);
            if (markedIndex >= 0) {
                causes[markedIndex].addSuppressed(RetriesExhaustedException.INSTANCE);
            }

            assertThat(DialogueRetries.isRetriesExhausted(causes[0]))
                    .as("prefix %s, cycle %s, marker %s", prefixLength, cycleLength, markedIndex)
                    .isEqualTo(markedIndex >= 0);
        }
    }

    private static final class SelfCausedException extends RuntimeException {
        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }

    private static final class SuppressionDisabledException extends RuntimeException {
        SuppressionDisabledException() {
            super("Suppression disabled", null, false, true);
        }
    }
}
