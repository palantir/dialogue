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

import com.google.common.base.Strings;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;

final class ApacheHttpClientChannel implements BlockingChannel {

    private final CloseableHttpClient client;
    private final UrlBuilder baseUrl;

    ApacheHttpClientChannel(CloseableHttpClient client, URL baseUrl) {
        this.client = client;
        // Sanitize path syntax and strip all irrelevant URL components
        Preconditions.checkArgument(
                null == Strings.emptyToNull(baseUrl.getQuery()),
                "baseUrl query must be empty",
                UnsafeArg.of("query", baseUrl.getQuery()));
        Preconditions.checkArgument(
                null == Strings.emptyToNull(baseUrl.getRef()),
                "baseUrl ref must be empty",
                UnsafeArg.of("ref", baseUrl.getRef()));
        Preconditions.checkArgument(
                null == Strings.emptyToNull(baseUrl.getUserInfo()), "baseUrl user info must be empty");
        this.baseUrl = UrlBuilder.withProtocol(baseUrl.getProtocol())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort());
        String strippedBasePath = stripSlashes(baseUrl.getPath());
        if (!strippedBasePath.isEmpty()) {
            this.baseUrl.encodedPathSegments(strippedBasePath);
        }
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        // Create base request given the URL
        UrlBuilder url = baseUrl.newBuilder();
        endpoint.renderPath(request.pathParams(), url);
        request.queryParams().forEach(url::queryParam);
        URL target = url.build();
        RequestBuilder builder = buildFor(endpoint, target);

        // Fill headers
        for (Map.Entry<String, String> header : request.headerParams().entrySet()) {
            builder.addHeader(header.getKey(), header.getValue());
        }

        if (request.body().isPresent()) {
            if (endpoint.httpMethod() == HttpMethod.GET) {
                throw new SafeIllegalArgumentException("GET endpoints must not have a request body");
            }
            if (endpoint.httpMethod() == HttpMethod.DELETE) {
                throw new SafeIllegalArgumentException("DELETE endpoints must not have a request body");
            }
            RequestBody body = request.body().get();
            builder.setEntity(
                    new InputStreamEntity(
                            body.content(), body.length().orElse(-1L), ContentType.parse(body.contentType())));
        }
        return new HttpClientResponse(client.execute(builder.build()));
    }

    private static RequestBuilder buildFor(Endpoint endpoint, URL url) {
        switch (endpoint.httpMethod()) {
            case GET:
                return RequestBuilder.get(url.toString());
            case POST:
                return RequestBuilder.post(url.toString());
            case PUT:
                return RequestBuilder.put(url.toString());
            case DELETE:
                return RequestBuilder.delete(url.toString());
        }
        throw new SafeIllegalArgumentException("Unknown request method", SafeArg.of("method", endpoint.httpMethod()));
    }

    private String stripSlashes(String path) {
        if (path.isEmpty()) {
            return path;
        } else if (path.equals("/")) {
            return "";
        } else {
            int stripStart = path.startsWith("/") ? 1 : 0;
            int stripEnd = path.endsWith("/") ? 1 : 0;
            return path.substring(stripStart, path.length() - stripEnd);
        }
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
                    tmpHeaders
                            .computeIfAbsent(header.getName(), _name -> new ArrayList<>(1))
                            .add(header.getValue());
                }
                headers = tmpHeaders;
            }
            return headers;
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            Header first = response.getFirstHeader(header);
            if (first != null) {
                return Optional.ofNullable(first.getValue());
            }
            return Optional.empty();
        }
    }
}
