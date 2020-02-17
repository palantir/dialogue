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

import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Headers;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;

final class ApacheHttpClientBlockingChannel implements BlockingChannel {

    private final CloseableHttpClient client;
    private final UrlBuilder baseUrl;

    ApacheHttpClientBlockingChannel(CloseableHttpClient client, URL baseUrl) {
        this.client = client;
        this.baseUrl = UrlBuilder.from(baseUrl);
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        // Create base request given the URL
        UrlBuilder url = baseUrl.newBuilder();
        endpoint.renderPath(request.pathParams(), url);
        request.queryParams().forEach(url::queryParam);
        URL target = url.build();
        RequestBuilder builder =
                RequestBuilder.create(endpoint.httpMethod().name()).setUri(target.toString());

        // Fill headers
        request.headerParams().forEach(builder::addHeader);

        if (request.body().isPresent()) {
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.GET, "GET endpoints must not have a request body");
            RequestBody body = request.body().get();
            builder.setEntity(new RequestBodyEntity(body));
        }
        return new HttpClientResponse(client.execute(builder.build()));
    }

    private static final class HttpClientResponse implements Response {

        private final CloseableHttpResponse response;
        private Map<String, List<String>> headers;

        HttpClientResponse(CloseableHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream body() {
            try {
                return response.getEntity().getContent();
            } catch (IOException e) {
                throw new SafeRuntimeException("Failed to get response stream", e);
            }
        }

        @Override
        public int code() {
            return response.getStatusLine().getStatusCode();
        }

        @Override
        public Map<String, List<String>> headers() {
            if (headers == null) {
                Map<String, List<String>> tmpHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (Header header : response.getAllHeaders()) {
                    String value = header.getValue();
                    if (value != null) {
                        tmpHeaders
                                .computeIfAbsent(header.getName(), _name -> new ArrayList<>(1))
                                .add(header.getValue());
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
    }

    private static final class RequestBodyEntity implements HttpEntity {

        private final RequestBody requestBody;
        private final Header contentType;

        RequestBodyEntity(RequestBody requestBody) {
            this.requestBody = requestBody;
            this.contentType = new BasicHeader(Headers.CONTENT_TYPE, requestBody.contentType());
        }

        @Override
        public boolean isRepeatable() {
            return false;
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
