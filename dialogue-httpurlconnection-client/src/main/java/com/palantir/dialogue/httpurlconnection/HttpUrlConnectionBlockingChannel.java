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
package com.palantir.dialogue.httpurlconnection;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.palantir.conjure.java.client.config.ClientConfiguration;
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
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.HttpsURLConnection;

final class HttpUrlConnectionBlockingChannel implements BlockingChannel {

    private final ClientConfiguration config;
    private final UrlBuilder baseUrl;

    HttpUrlConnectionBlockingChannel(ClientConfiguration config, URL baseUrl) {
        this.config = config;
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
        URLConnection urlConnection = url.build().openConnection();
        if (!(urlConnection instanceof HttpURLConnection)) {
            throw new SafeIllegalStateException(
                    "Expected an HttpUrlConnection", SafeArg.of("actual", urlConnection.getClass()));
        }
        HttpURLConnection connection = (HttpURLConnection) urlConnection;
        connection.setRequestMethod(endpoint.httpMethod().name());

        // Fill headers
        for (Map.Entry<String, String> header : request.headerParams().entrySet()) {
            connection.addRequestProperty(header.getKey(), header.getValue());
        }

        connection.setRequestProperty("accept-encoding", "gzip");

        connection.setConnectTimeout(Ints.checkedCast(config.connectTimeout().toMillis()));
        connection.setReadTimeout(Ints.checkedCast(config.readTimeout().toMillis()));

        // match okhttp behavior
        connection.setInstanceFollowRedirects(true);

        // Never ask users for credentials
        connection.setAllowUserInteraction(false);
        connection.setDoOutput(request.body().isPresent());
        connection.setDoInput(true);

        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            httpsConnection.setSSLSocketFactory(config.sslSocketFactory());
            // TODO(ckozak): hostname verifier, otherwise the default is used.
            // It's likely fine for most scenarios, but we want consistency
            // across all channels.
        }

        if (request.body().isPresent()) {
            if (endpoint.httpMethod() == HttpMethod.GET) {
                throw new SafeIllegalArgumentException("GET endpoints must not have a request body");
            }
            if (endpoint.httpMethod() == HttpMethod.DELETE) {
                throw new SafeIllegalArgumentException("DELETE endpoints must not have a request body");
            }
            RequestBody body = request.body().get();
            if (body.length().isPresent()) {
                connection.setFixedLengthStreamingMode(body.length().getAsLong());
            } else {
                connection.setChunkedStreamingMode(1024 * 8);
            }
            connection.setRequestProperty("content-type", body.contentType());
            try (OutputStream requestBodyStream = connection.getOutputStream()) {
                ByteStreams.copy(body.content(), requestBodyStream);
            }
        }
        return new HttpUrlConnectionResponse(connection);
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

    @Override
    public String toString() {
        return "HttpUrlConnectionBlockingChannel{baseUrl=" + baseUrl.build() + '}';
    }

    private static final class HttpUrlConnectionResponse implements Response {

        private final HttpURLConnection connection;
        private final int code;

        HttpUrlConnectionResponse(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            // blocks until the response is received
            this.code = connection.getResponseCode();
        }

        @Override
        public InputStream body() {
            if (code >= 400) {
                return connection.getErrorStream();
            }
            try {
                return connection.getInputStream();
            } catch (IOException e) {
                throw new SafeRuntimeException("Failed to read response stream", e);
            }
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public Map<String, List<String>> headers() {
            return connection.getHeaderFields();
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Optional.ofNullable(connection.getHeaderField(header));
        }

        @Override
        public String toString() {
            return "HttpUrlConnectionResponse{connection=" + connection + ", code=" + code + '}';
        }
    }
}
