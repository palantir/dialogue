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
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.HttpsURLConnection;

final class HttpUrlConnectionBlockingChannel implements BlockingChannel {

    private final ClientConfiguration config;
    private final UrlBuilder baseUrl;

    HttpUrlConnectionBlockingChannel(ClientConfiguration config, URL baseUrl) {
        this.config = config;
        this.baseUrl = UrlBuilder.from(baseUrl);
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        // Create base request given the URL
        UrlBuilder url = baseUrl.newBuilder();
        endpoint.renderPath(request.pathParams(), url);
        request.queryParams().forEach(url::queryParam);
        HttpURLConnection connection = (HttpURLConnection) url.build().openConnection();
        connection.setRequestMethod(endpoint.httpMethod().name());

        // Fill headers
        request.headerParams().forEach(connection::addRequestProperty);

        connection.setConnectTimeout(Ints.checkedCast(config.connectTimeout().toMillis()));
        connection.setReadTimeout(Ints.checkedCast(config.readTimeout().toMillis()));

        // match okhttp behavior
        connection.setInstanceFollowRedirects(false);

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
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.GET, "GET endpoints must not have a request body");
            RequestBody body = request.body().get();
            connection.setChunkedStreamingMode(1024 * 8);
            connection.setRequestProperty("content-type", body.contentType());
            try (OutputStream requestBodyStream = connection.getOutputStream()) {
                body.writeTo(requestBodyStream);
            }
        }
        return new HttpUrlConnectionResponse(connection);
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
