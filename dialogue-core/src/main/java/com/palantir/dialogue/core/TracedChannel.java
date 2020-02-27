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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.Tracers;
import java.util.Objects;

final class TracedChannel implements Channel {
    private final Channel delegate;
    private final String operationName;

    TracedChannel(Channel delegate, String operationName) {
        this.delegate = delegate;
        this.operationName = operationName;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return Tracers.wrapListenableFuture(operationName, () -> delegate.execute(endpoint, request));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TracedChannel that = (TracedChannel) o;
        return delegate.equals(that.delegate) && operationName.equals(that.operationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, operationName);
    }

    @Override
    public String toString() {
        return "TracedChannel{delegate=" + delegate + ", operationName='" + operationName + "\'}";
    }
}
