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

import com.google.common.base.Suppliers;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachments;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Signals that a failure already went through a full Dialogue retry loop, at this hop or below it.
 *
 * <p>The signal is the {@code Dialogue-Retries-Exhausted: true} header. When retries are exhausted, the final response
 * is returned with that header, and the error decoder records it in the diagnostic attached to the decoded exception.
 * An exhausted network failure gets an equivalent diagnostic. A response that already carried the header keeps it, so
 * the signal also passes through hops that did not retry themselves. Servers read the diagnostic from the failure they
 * are about to return and set the header on their own response.
 */
final class RetriesExhausted {

    private RetriesExhausted() {}

    /** Returns {@code response} with the retries-exhausted header, wrapping it unless it already has the header. */
    static Response withHeader(Response response) {
        return Responses.hasRetriesExhaustedHeader(response) ? response : new RetriesExhaustedResponse(response);
    }

    /** Tags an exhausted network failure with the same diagnostic the error decoder records for the header. */
    static void addDiagnostic(Throwable failure) {
        for (Throwable suppressed : failure.getSuppressed()) {
            if (suppressed instanceof RetriesExhaustedDiagnostic) {
                return;
            }
        }
        failure.addSuppressed(new RetriesExhaustedDiagnostic());
    }

    private static final class RetriesExhaustedResponse implements Response {
        private static final Optional<String> TRUE = Optional.of("true");

        private final Response delegate;
        private final Supplier<ListMultimap<String, String>> headers;

        RetriesExhaustedResponse(Response delegate) {
            this.delegate = delegate;
            // Built only if all headers are requested; the error decoder only uses getFirstHeader.
            this.headers = Suppliers.memoize(() -> {
                ListMultimap<String, String> combined = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                        .arrayListValues()
                        .build(delegate.headers());
                combined.put(Responses.RETRIES_EXHAUSTED, "true");
                return Multimaps.unmodifiableListMultimap(combined);
            });
        }

        @Override
        public InputStream body() {
            return delegate.body();
        }

        @Override
        public int code() {
            return delegate.code();
        }

        @Override
        public ListMultimap<String, String> headers() {
            return headers.get();
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Responses.RETRIES_EXHAUSTED.equalsIgnoreCase(header) ? TRUE : delegate.getFirstHeader(header);
        }

        @Override
        public ResponseAttachments attachments() {
            return delegate.attachments();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public String toString() {
            return "RetriesExhaustedResponse{delegate=" + delegate + '}';
        }
    }

    private static final class RetriesExhaustedDiagnostic extends RuntimeException implements SafeLoggable {
        private static final String SAFE_MESSAGE = "Dialogue retries exhausted";
        private static final List<Arg<?>> ARGS = List.of(SafeArg.of(Responses.RETRIES_EXHAUSTED, "true"));

        RetriesExhaustedDiagnostic() {
            // No stack trace: this only tags the failure it is attached to.
            super(SAFE_MESSAGE, null, false, false);
        }

        @Override
        public String getLogMessage() {
            return SAFE_MESSAGE;
        }

        @Override
        public List<Arg<?>> getArgs() {
            return ARGS;
        }
    }
}
