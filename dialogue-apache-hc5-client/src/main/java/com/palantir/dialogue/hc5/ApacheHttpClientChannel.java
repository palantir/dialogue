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
package com.palantir.dialogue.hc5;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BaseUrl;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientChannel implements Channel {
    private static final Logger log = LoggerFactory.getLogger(ApacheHttpClientChannel.class);

    private final ApacheHttpClientChannels.CloseableClient client;
    private final BaseUrl baseUrl;
    private final ResponseLeakDetector responseLeakDetector;

    ApacheHttpClientChannel(
            ApacheHttpClientChannels.CloseableClient client, URL baseUrl, ResponseLeakDetector responseLeakDetector) {
        this.client = client;
        this.baseUrl = BaseUrl.of(baseUrl);
        this.responseLeakDetector = responseLeakDetector;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        try {
            // Create base request given the URL
            URL target = baseUrl.render(endpoint, request);
            SimpleHttpRequest hcRequest =
                    SimpleHttpRequests.create(endpoint.httpMethod().name(), target.toString());

            // Fill headers
            request.headerParams().forEach(hcRequest::addHeader);

            if (request.body().isPresent()) {
                Preconditions.checkArgument(
                        endpoint.httpMethod() != HttpMethod.GET, "GET endpoints must not have a request body");
                Preconditions.checkArgument(
                        endpoint.httpMethod() != HttpMethod.HEAD, "HEAD endpoints must not have a request body");
                Preconditions.checkArgument(
                        endpoint.httpMethod() != HttpMethod.OPTIONS, "OPTIONS endpoints must not have a request body");
                RequestBody body = request.body().get();
                setBody(hcRequest, body);
            }

            SettableFuture<Response> result = SettableFuture.create();
            Future<SimpleHttpResponse> callFuture = client.apacheClient()
                    .execute(hcRequest, new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(SimpleHttpResponse response) {
                            Response dialogueResponse = new HttpClientResponse(response);
                            Response leakDetectingResponse = responseLeakDetector.wrap(dialogueResponse, endpoint);
                            if (!result.set(leakDetectingResponse)) {
                                log.info("Result future has already completed");
                            }
                        }

                        @Override
                        public void failed(Exception ex) {
                            if (!result.setException(ex)) {
                                log.info("Result future has already completed");
                            }
                        }

                        @Override
                        public void cancelled() {
                            result.cancel(false);
                        }
                    });
            // propagate cancellation
            result.addListener(() -> callFuture.cancel(false), MoreExecutors.directExecutor());
            return result;
        } catch (Throwable t) {
            return Futures.immediateFailedFuture(t);
        }
    }

    private static void setBody(SimpleHttpRequest builder, RequestBody body) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            body.writeTo(baos);
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to buffer request data", e);
        }
        builder.setBody(baos.toByteArray(), ContentType.create(body.contentType()));
    }

    private static final class HttpClientResponse implements Response {

        private final SimpleHttpResponse response;

        @Nullable
        private ListMultimap<String, String> headers;

        HttpClientResponse(SimpleHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream body() {
            byte[] bytes = response.getBodyBytes();
            if (bytes != null) {
                return new ByteArrayInputStream(response.getBodyBytes());
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return response.getCode();
        }

        @Override
        public ListMultimap<String, String> headers() {
            if (headers == null) {
                ListMultimap<String, String> tmpHeaders = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                        .arrayListValues()
                        .build();
                Iterator<Header> headerIterator = response.headerIterator();
                while (headerIterator.hasNext()) {
                    Header header = headerIterator.next();
                    String value = header.getValue();
                    if (value != null) {
                        tmpHeaders.put(header.getName(), value);
                    }
                }
                headers = Multimaps.unmodifiableListMultimap(tmpHeaders);
            }
            return headers;
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Optional.ofNullable(response.getFirstHeader(header)).map(Header::getValue);
        }

        @Override
        public void close() {}

        @Override
        public String toString() {
            return "HttpClientResponse{response=" + response + '}';
        }
    }
}
