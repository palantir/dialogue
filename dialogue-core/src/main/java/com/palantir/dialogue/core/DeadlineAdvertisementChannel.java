/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;

final class DeadlineAdvertisementChannel implements Channel {

    private final Channel delegate;
    private final Duration readTimeout;
    private final Deadlines.Enforcement enforcement;

    static DeadlineAdvertisementChannel create(Channel delegate, Duration readTimeout) {
        return new DeadlineAdvertisementChannel(delegate, readTimeout, Deadlines.Enforcement.DEFER);
    }

    static DeadlineAdvertisementChannel create(Channel delegate, Duration readTimeout, boolean enforcementEnabled) {
        return new DeadlineAdvertisementChannel(
                delegate,
                readTimeout,
                enforcementEnabled ? Deadlines.Enforcement.ENFORCE : Deadlines.Enforcement.DISABLE);
    }

    private DeadlineAdvertisementChannel(Channel delegate, Duration readTimeout, Deadlines.Enforcement enforcement) {
        this.delegate = delegate;
        // a readTimeout of zero effectively means "no timeout", but we don't want to put 0 on the wire,
        // so set a very large value instead
        // this matches the behavior in ApacheHttpClientChannels
        // see:
        // https://github.com/palantir/dialogue/blob/develop/dialogue-apache-hc5-client/src/main/java/com/palantir/dialogue/hc5/ApacheHttpClientChannels.java#L641-L648
        this.readTimeout = readTimeout.isNegative() || readTimeout.isZero() ? Duration.ofDays(1) : readTimeout;
        this.enforcement = enforcement;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Request.Builder requestBuilder = Request.builder().from(request);
        try {
            Deadlines.encodeToRequest(readTimeout, requestBuilder, RequestBuilderEncodingAdapter.INSTANCE, enforcement);
        } catch (DeadlineExpiredException e) {
            return Futures.immediateFailedFuture(e);
        }
        return delegate.execute(endpoint, requestBuilder.build());
    }

    private enum RequestBuilderEncodingAdapter implements Deadlines.RequestEncodingAdapter<Request.Builder> {
        INSTANCE;

        @Override
        public void setHeader(Request.Builder builder, String headerName, String headerValue) {
            builder.putHeaderParams(headerName, headerValue);
        }
    }
}
