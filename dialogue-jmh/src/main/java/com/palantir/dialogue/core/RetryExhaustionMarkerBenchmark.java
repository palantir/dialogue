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

import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.dialogue.RetriesExhaustedException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/** Compares exception metadata strategies only; this is not an HTTP or retry-loop benchmark. */
@Warmup(iterations = 4, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(
        value = 2,
        jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
@Threads(1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
// Raw JDK exceptions are intentional benchmark inputs.
@SuppressWarnings({"VisibilityModifier", "DesignForExtension", "PreferSafeLoggableExceptions"})
public class RetryExhaustionMarkerBenchmark {
    @SuppressWarnings(
            "StaticAssignmentOfThrowable") // Immutable, stackless metadata deliberately shared across failures.
    private static final ExhaustionMarker SHARED_MARKER = new ExhaustionMarker();

    @SuppressWarnings("StaticAssignmentOfThrowable") // Stand-in for metadata already attached by the HTTP decoder.
    private static final UnrelatedDiagnostic EXISTING_DIAGNOSTIC = new UnrelatedDiagnostic();

    private static final SerializableError REMOTE_ERROR = SerializableError.builder()
            .errorCode("INTERNAL")
            .errorName("Benchmark:Failure")
            .errorInstanceId("00000000-0000-0000-0000-000000000001")
            .build();

    @Benchmark
    public Throwable original(FailureState state) {
        return state.newFailure();
    }

    @Benchmark
    public Throwable currentWrapper(FailureState state) {
        return new RetriesExhaustedException(state.newFailure());
    }

    @Benchmark
    public Throwable newStacklessMarker(FailureState state) {
        Throwable failure = state.newFailure();
        if (!hasMarker(failure)) {
            failure.addSuppressed(new ExhaustionMarker());
        }
        return failure;
    }

    @Benchmark
    public Throwable sharedStacklessMarker(FailureState state) {
        Throwable failure = state.newFailure();
        if (!hasMarker(failure)) {
            failure.addSuppressed(SHARED_MARKER);
        }
        return failure;
    }

    @Benchmark
    public boolean detectMarker(DetectionState state) {
        return hasMarker(state.failure);
    }

    // Benchmark prototype: scans the ordinary, acyclic cause chains constructed below. A production helper
    // would also need a policy for cyclic/malformed cause chains and exceptions with suppression disabled.
    private static boolean hasMarker(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed instanceof ExhaustionMarker) {
                    return true;
                }
            }
        }
        return false;
    }

    @State(Scope.Thread)
    public static class FailureState {
        @Param({"IO", "QOS", "REMOTE"})
        public String failureType;

        Throwable newFailure() {
            Throwable failure = switch (failureType) {
                case "IO" -> new IOException("Socket failed");
                case "QOS" -> QosException.unavailable();
                case "REMOTE" -> new RemoteException(REMOTE_ERROR, 500);
                default -> throw new IllegalArgumentException("Unknown failure type");
            };
            if (!failureType.equals("IO")) {
                // HTTP error decoding already attaches a stackless response diagnostic. Reuse a stand-in
                // to reproduce the suppressed-list shape without measuring diagnostic message formatting.
                failure.addSuppressed(EXISTING_DIAGNOSTIC);
            }
            return failure;
        }
    }

    @State(Scope.Thread)
    public static class DetectionState {
        @Param({"0", "1", "4"})
        public int causeDepth;

        @Param({"false", "true"})
        public boolean marked;

        private Throwable failure;

        @Setup
        public void before() {
            failure = new IOException("Socket failed");
            failure.addSuppressed(EXISTING_DIAGNOSTIC);
            if (marked) {
                failure.addSuppressed(SHARED_MARKER);
            }
            for (int i = 0; i < causeDepth; i++) {
                failure = new RuntimeException(failure);
                failure.addSuppressed(EXISTING_DIAGNOSTIC);
            }
            if (hasMarker(failure) != marked) {
                throw new IllegalStateException("Marker lookup did not match benchmark setup");
            }
        }
    }

    // This object carries no request-specific state and never captures a stack trace. Suppression is
    // disabled on the marker itself, while the original exception still accepts suppressed metadata.
    private static final class ExhaustionMarker extends RuntimeException {
        ExhaustionMarker() {
            super("Dialogue retries exhausted", null, false, false);
        }
    }

    private static final class UnrelatedDiagnostic extends RuntimeException {
        UnrelatedDiagnostic() {
            super("Existing response diagnostic", null, false, false);
        }
    }
}
