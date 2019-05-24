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
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HttpChannel implements Channel {

    private final HttpClient client;
    private final UrlBuilder baseUrl;

    private HttpChannel(HttpClient client, URL baseUrl) {
        this.client = client;
        // Sanitize path syntax and strip all irrelevant URL components
        Preconditions.checkArgument(null == Strings.emptyToNull(baseUrl.getQuery()),
                "baseUrl query must be empty", UnsafeArg.of("query", baseUrl.getQuery()));
        Preconditions.checkArgument(null == Strings.emptyToNull(baseUrl.getRef()),
                "baseUrl ref must be empty", UnsafeArg.of("ref", baseUrl.getRef()));
        Preconditions.checkArgument(
                null == Strings.emptyToNull(baseUrl.getUserInfo()),
                "baseUrl user info must be empty");
        this.baseUrl = UrlBuilder.withProtocol(baseUrl.getProtocol())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort());
        String strippedBasePath = stripSlashes(baseUrl.getPath());
        if (!strippedBasePath.isEmpty()) {
            this.baseUrl.encodedPathSegments(strippedBasePath);
        }
    }

    public static HttpChannel of(HttpClient client, URL baseUrl) {
        return new HttpChannel(client, baseUrl);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        // Create base request given the URL
        UrlBuilder url = baseUrl.newBuilder();
        endpoint.renderPath(request.pathParams(), url);
        request.queryParams().forEach(url::queryParam);
        final HttpRequest.Builder httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder().uri(url.build().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to construct URI, this is a bug", e);
        }

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
                        !request.body().isPresent(), "DELETE endpoints must not have a request body");
                httpRequest.DELETE();
                break;
        }

        // Fill headers
        for (Map.Entry<String, String> header : request.headerParams().entrySet()) {
            httpRequest.header(header.getKey(), header.getValue());
        }

        // TODO(rfink): Think about repeatability/retries

        CompletableFuture<Response> future = client.sendAsync(
                httpRequest.build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(this::toResponse);

        return new CompletableToListenableFuture<>(future);
    }

    private Response toResponse(HttpResponse<InputStream> response) {
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
            public Map<String, List<String>> headers() {
                return response.headers().map();
            }
        };
    }

    private static HttpRequest.BodyPublisher toBody(Request request, String method) {
        RequestBody body = request.body().orElseThrow(() -> new SafeIllegalArgumentException(
                "Endpoint must have a request body", SafeArg.of("method", method)));
        // TODO(rfink): Throw if accessed multiple times?
        return HttpRequest.BodyPublishers.ofInputStream(body::content);
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
}
