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

package com.palantir.dialogue.hc5;

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.http.protocol.HttpContext;

/** Reports a request that failed before any of its socket writes completed as {@link SafeRequestNotSentException}. */
final class RequestNotSentDetectingHttpRequestExecutor extends HttpRequestExecutor {
    static final RequestNotSentDetectingHttpRequestExecutor INSTANCE = new RequestNotSentDetectingHttpRequestExecutor();

    private RequestNotSentDetectingHttpRequestExecutor() {}

    @Override
    public ClassicHttpResponse execute(
            ClassicHttpRequest request,
            HttpClientConnection conn,
            HttpResponseInformationCallback informationCallback,
            HttpContext context)
            throws IOException, HttpException {
        EndpointDetails endpointDetails = conn.getEndpointDetails();
        long sentBytesBeforeRequest = endpointDetails.getSentBytesCount();
        try {
            return super.execute(request, conn, informationCallback, context);
        } catch (IOException exception) {
            // Apache counts bytes only once a socket write returns, so no change means no write completed.
            if (endpointDetails.getSentBytesCount() == sentBytesBeforeRequest) {
                throw new SafeRequestNotSentException(exception);
            }
            throw exception;
        }
    }
}
