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

import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.primitives.Ints;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachments;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.core.BaseUrl;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.HttpsURLConnection;

final class HttpUrlConnectionBlockingChannel implements BlockingChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(HttpUrlConnectionBlockingChannel.class);

    private final ClientConfiguration config;
    private final BaseUrl baseUrl;

    HttpUrlConnectionBlockingChannel(ClientConfiguration config, URL baseUrl) {
        this.config = config;
        this.baseUrl = BaseUrl.of(baseUrl);
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        // Create base request given the URL
        HttpURLConnection connection =
                (HttpURLConnection) baseUrl.render(endpoint, request).openConnection();
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
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.HEAD, "HEAD endpoints must not have a request body");
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.OPTIONS, "OPTIONS endpoints must not have a request body");
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
        return "HttpUrlConnectionBlockingChannel{baseUrl=" + baseUrl + '}';
    }

    private static final class HttpUrlConnectionResponse implements Response {

        private final HttpURLConnection connection;
        private final int code;
        private final ResponseAttachments attachments = ResponseAttachments.create();

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
        public ListMultimap<String, String> headers() {
            ListMultimap<String, String> headers = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                    .arrayListValues()
                    .build();
            connection.getHeaderFields().forEach((headerName, headerValues) -> {
                if (headerName != null) {
                    headers.putAll(headerName, Iterables.filter(headerValues, Objects::nonNull));
                }
            });
            return headers;
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Optional.ofNullable(connection.getHeaderField(header));
        }

        @Override
        public ResponseAttachments attachments() {
            return attachments;
        }

        @Override
        public void close() {
            try {
                body().close();
            } catch (IOException e) {
                log.warn("Failed to close response", e);
            }
        }

        @Override
        public String toString() {
            return "HttpUrlConnectionResponse{connection=" + connection + ", code=" + code + '}';
        }
    }
}
