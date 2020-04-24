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
package com.palantir.dialogue.hc4;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.net.HttpHeaders;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.core.BaseUrl;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientBlockingChannel implements BlockingChannel {
    private static final Logger log = LoggerFactory.getLogger(ApacheHttpClientBlockingChannel.class);

    private final CloseableHttpClient client;
    private final BaseUrl baseUrl;
    private final ResponseLeakDetector responseLeakDetector;

    ApacheHttpClientBlockingChannel(
            CloseableHttpClient client, URL baseUrl, ResponseLeakDetector responseLeakDetector) {
        this.client = client;
        this.baseUrl = BaseUrl.of(baseUrl);
        this.responseLeakDetector = responseLeakDetector;
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        // Create base request given the URL
        URL target = baseUrl.render(endpoint, request);
        RequestBuilder builder =
                RequestBuilder.create(endpoint.httpMethod().name()).setUri(target.toString());

        // Fill headers
        request.headerParams().forEach(builder::addHeader);

        if (request.body().isPresent()) {
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.GET, "GET endpoints must not have a request body");
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.HEAD, "HEAD endpoints must not have a request body");
            RequestBody body = request.body().get();
            builder.setEntity(new RequestBodyEntity(body));
        }
        CloseableHttpResponse httpClientResponse = client.execute(builder.build());
        // Defensively ensure that resources are closed if failures occur within this block,
        // for example HttpClientResponse allocation may throw an OutOfMemoryError.
        boolean close = true;
        try {
            Response dialogueResponse = new HttpClientResponse(httpClientResponse);
            Response leakDetectingResponse = responseLeakDetector.wrap(dialogueResponse, endpoint);
            close = false;
            return leakDetectingResponse;
        } finally {
            if (close) {
                httpClientResponse.close();
            }
        }
    }

    private static final class HttpClientResponse implements Response {

        private final CloseableHttpResponse response;

        @Nullable
        private ListMultimap<String, String> headers;

        HttpClientResponse(CloseableHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream body() {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    return entity.getContent();
                } catch (IOException e) {
                    throw new SafeRuntimeException("Failed to get response stream", e);
                }
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return response.getStatusLine().getStatusCode();
        }

        @Override
        public ListMultimap<String, String> headers() {
            if (headers == null) {
                ListMultimap<String, String> tmpHeaders = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                        .arrayListValues()
                        .build();
                HeaderIterator headerIterator = response.headerIterator();
                while (headerIterator.hasNext()) {
                    Header header = headerIterator.nextHeader();
                    String value = header.getValue();
                    if (value != null) {
                        tmpHeaders.put(header.getName(), value);
                    }
                }
                headers = tmpHeaders;
            }
            return headers;
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Optional.ofNullable(response.getFirstHeader(header)).map(Header::getValue);
        }

        @Override
        public void close() {
            try {
                response.close();
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to close response", e);
            }
        }

        @Override
        public String toString() {
            return "HttpClientResponse{response=" + response + '}';
        }
    }

    private static final class RequestBodyEntity implements HttpEntity {

        private final RequestBody requestBody;
        private final Header contentType;

        RequestBodyEntity(RequestBody requestBody) {
            this.requestBody = requestBody;
            this.contentType = new BasicHeader(HttpHeaders.CONTENT_TYPE, requestBody.contentType());
        }

        @Override
        public boolean isRepeatable() {
            // n.b. Proxy authentication can only be negotiated on repeatable requests.
            // Subsequent requests needn't be repeatable as state is cached by the client.
            return requestBody.repeatable();
        }

        @Override
        public boolean isChunked() {
            return true;
        }

        @Override
        public long getContentLength() {
            // unknown
            return -1;
        }

        @Override
        public Header getContentType() {
            return contentType;
        }

        @Override
        @Nullable
        public Header getContentEncoding() {
            return null;
        }

        @Override
        public InputStream getContent() throws UnsupportedOperationException {
            throw new UnsupportedOperationException("getContent is not supported, writeTo should be used");
        }

        @Override
        public void writeTo(OutputStream outStream) throws IOException {
            requestBody.writeTo(outStream);
        }

        @Override
        public boolean isStreaming() {
            // Applies to responses.
            return false;
        }

        @Override
        public void consumeContent() {}

        @Override
        public String toString() {
            return "RequestBodyEntity{requestBody=" + requestBody + '}';
        }
    }
}
