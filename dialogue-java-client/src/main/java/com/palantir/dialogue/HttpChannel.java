/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import com.google.common.base.Strings;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class HttpChannel implements Channel {

    private final HttpClient client;
    private final URL baseUrl;
    private final ErrorDecoder errorDecoder;

    private HttpChannel(HttpClient client, URL baseUrl, ErrorDecoder errorDecoder) {
        this.client = client;
        // Sanitize path syntax and strip all irrelevant URL components
        Preconditions.checkArgument(null == Strings.emptyToNull(baseUrl.getQuery()),
                "baseUrl query must be empty", UnsafeArg.of("query", baseUrl.getQuery()));
        Preconditions.checkArgument(null == Strings.emptyToNull(baseUrl.getRef()),
                "baseUrl ref must be empty", UnsafeArg.of("ref", baseUrl.getRef()));
        Preconditions.checkArgument(
                null == Strings.emptyToNull(baseUrl.getUserInfo()),
                "baseUrl user info must be empty");
        String basePath = baseUrl.getPath().endsWith("/")
                ? baseUrl.getPath().substring(0, baseUrl.getPath().length() - 1)
                : baseUrl.getPath();
        this.baseUrl = Urls.create(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(), basePath);
        this.errorDecoder = errorDecoder;
    }

    public static HttpChannel of(HttpClient client, URL baseUrl, ErrorDecoder errorDecoder) {
        return new HttpChannel(client, baseUrl, errorDecoder);
    }

    @SuppressWarnings("FutureReturnValueIgnored")  // TODO(rfink): What to do with the future?
    @Override
    public Call createCall(Endpoint endpoint, Request request) {
        // Create base request given the URL
        String endpointPath = endpoint.renderPath(request.pathParams());
        Preconditions.checkArgument(endpointPath.startsWith("/"), "endpoint path must start with /");
        // Concatenation is OK since base path is empty or starts with / and does not end with / ,
        // and endpoint path starts with /
        String effectivePath = baseUrl.getPath() + endpointPath;

        // TODO(rfink): URL-encode
        final URI uri;
        try {
            uri = new URI(baseUrl.getProtocol(), null, baseUrl.getHost(), baseUrl.getPort(), effectivePath, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct URL", e);
        }
        HttpRequest.Builder httpRequest = HttpRequest.newBuilder().uri(uri);

        // TODO(rfink): Query params
        // request.queryParams().entries().forEach(entry -> url.addQueryParameter(entry.getKey(), entry.getValue()));

        // Fill request body and set HTTP method
        switch (endpoint.httpMethod()) {
            case GET:
                Preconditions.checkArgument(!request.body().isPresent(), "GET endpoints must not have a request body");
                httpRequest.GET();
                break;
            case POST:
                httpRequest.POST(toBody(request, "POST"));
                break;
            case PUT:
                httpRequest.PUT(toBody(request, "PUT"));
                break;
            case DELETE:
                Preconditions.checkArgument(
                        !request.body().isPresent(),
                        "DELETE endpoints must not have a request body");
                httpRequest.DELETE();
                break;
        }

        // Fill headers
        for (Map.Entry<String, String> header : request.headerParams().entrySet()) {
            httpRequest.header(header.getKey(), header.getValue());
        }

        CompletableFuture<HttpResponse<InputStream>> call = client.sendAsync(
                httpRequest.build(),
                HttpResponse.BodyHandlers.ofInputStream());
        return new Call() {
            @Override
            public void execute(Observer observer) {
                // TODO(rfink): What to do with this future?
                call
                        .thenAccept(httpResponse -> {
                            Response response = toResponse(httpResponse);
                            if (isSuccessful(response.code())) {
                                observer.success(response);
                            } else {
                                observer.failure(errorDecoder.decode(response));
                            }
                        })
                        .exceptionally(exception -> {
                            observer.exception(exception);
                            return null;
                        });
            }

            @Override
            public void cancel() {
                call.cancel(true);
            }
        };
    }

    private boolean isSuccessful(int code) {
        return code >= 200 && code < 300;
    }

    private static Response toResponse(HttpResponse<InputStream> response) {
        return new Response() {
            @Override
            public InputStream body() {
                return response.body();
            }

            @Override
            public int code() {
                return response.statusCode();
            }

            @Override
            public Optional<String> contentType() {
                // TODO(rfink): Header case sensitivity?
                return response.headers().firstValue(Headers.CONTENT_TYPE);
            }
        };
    }

    private static HttpRequest.BodyPublisher toBody(Request request, String method) {
        RequestBody body = request.body().orElseThrow(() -> new SafeIllegalArgumentException(
                "Endpoint must have a request body", SafeArg.of("method", method)));
        // TODO(rfink): Throw if accessed multiple times?
        return HttpRequest.BodyPublishers.ofInputStream(body::content);
    }
}
