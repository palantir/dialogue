/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ListMultimap;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

/**
 * Range requests generally don't support content-compression in responses due to ambiguity around whether the
 * byte-range refers to compressed content, or the original content, which are completely different.
 */
final class RangeAcceptsIdentityEncodingChannel implements EndpointChannel {

    private final EndpointChannel delegate;

    RangeAcceptsIdentityEncodingChannel(EndpointChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        Request delegateRequest =
                isRangeRequestWithoutAcceptEncoding(request) ? withIdentityEncoding(request) : request;
        return delegate.execute(delegateRequest);
    }

    private static boolean isRangeRequestWithoutAcceptEncoding(Request request) {
        ListMultimap<String, String> requestHeaders = request.headerParams();
        return requestHeaders.containsKey(HttpHeaders.RANGE)
                && !requestHeaders.containsKey(HttpHeaders.ACCEPT_ENCODING);
    }

    private static Request withIdentityEncoding(Request request) {
        return Request.builder()
                .from(request)
                .putHeaderParams(HttpHeaders.ACCEPT_ENCODING, "identity")
                .build();
    }
}
