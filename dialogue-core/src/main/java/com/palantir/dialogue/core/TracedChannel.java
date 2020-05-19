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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.Tracer;

final class TracedChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final String operationName;

    TracedChannel(EndpointChannel delegate, String operationName) {
        this.delegate = delegate;
        this.operationName = operationName;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        if (!Tracer.isTraceObservable()) {
            return delegate.execute(request);
        }

        DetachedSpan span = DetachedSpan.start(operationName);
        ListenableFuture<Response> result = null;
        // n.b. This span is required to apply tracing thread state to an initial request. Otherwise if there is
        // no active trace, the detached span would not be associated with work initiated by delegateFactory.
        try (CloseableSpan ignored =
                // This could be more efficient using https://github.com/palantir/tracing-java/issues/177
                span.childSpan(operationName + " initial")) {
            result = delegate.execute(request);
        } finally {
            if (result != null) {
                // In the successful case we add a listener in the finally block to prevent confusing traces
                // when delegateFactory returns a completed future. This way the detached span cannot complete
                // prior to its child.
                result.addListener(span::complete, MoreExecutors.directExecutor());
            } else {
                // Complete the detached span, even if the delegateFactory throws.
                span.complete();
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "TracedChannel{operationName=" + operationName + ", delegate=" + delegate + '}';
    }
}
