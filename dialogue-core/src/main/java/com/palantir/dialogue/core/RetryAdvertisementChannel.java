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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

/**
 * Adds the {@code Client-Can-Retry: true} to inform proxies that this client is capable of retrying requests.
 * Note that this channel does not check whether the request body itself is retryable, nor that the current
 * configuration is configured to allow retries. This is intentional, because this flag is designed to inform
 * the proxy of whether not the responsibility for retries can be left to the client, regardless of the current
 * configuration.
 * <p/>
 * This feature functions in combination with {@code Proxy-Upstream-Request-Attempts} to allow negotiation for
 * retries to occur based on proxy configuration, without requiring clients to be updated or reconfigured.
 */
final class RetryAdvertisementChannel implements EndpointChannel {

    @VisibleForTesting
    static final String CLIENT_CAN_RETRY_HEADER = "Client-Can-Retry";

    private static final String CLIENT_CAN_RETRY_VALUE = "true";

    private final EndpointChannel delegate;

    RetryAdvertisementChannel(EndpointChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        return delegate.execute(augment(request));
    }

    private static Request augment(Request input) {
        if (input.headerParams().containsKey(CLIENT_CAN_RETRY_HEADER)) {
            return input;
        }
        return Request.builder()
                .from(input)
                .putHeaderParams(CLIENT_CAN_RETRY_HEADER, CLIENT_CAN_RETRY_VALUE)
                .build();
    }

    @Override
    public String toString() {
        return "RetryAdvertisementChannel{" + delegate + '}';
    }
}
