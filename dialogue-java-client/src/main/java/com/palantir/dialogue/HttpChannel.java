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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.core.BaseUrl;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpChannel implements Channel {
    private static final Logger log = LoggerFactory.getLogger(HttpChannel.class);

    private final HttpClient client;
    private final Duration requestTimeout;
    private final BaseUrl baseUrl;

    private HttpChannel(HttpClient client, URL baseUrl, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.baseUrl = BaseUrl.of(baseUrl);
    }

    public static HttpChannel of(HttpClient client, URL baseUrl) {
        return new HttpChannel(client, baseUrl, Duration.ofSeconds(30));
    }

    public static HttpChannel of(HttpClient client, URL baseUrl, Duration requestTimeout) {
        return new HttpChannel(client, baseUrl, requestTimeout);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        // Create base request given the URL
        HttpRequest.Builder httpRequest = newRequestBuilder(baseUrl.render(endpoint, request));

        // Fill request body and set HTTP method
        Preconditions.checkArgument(
                !(request.body().isPresent() && endpoint.httpMethod() == HttpMethod.GET),
                "GET endpoints must not have a request body");
        httpRequest.method(endpoint.httpMethod().name(), toBody(request));

        // Fill headers
        request.headerParams().forEach(httpRequest::header);

        request.body().ifPresent(body -> httpRequest.header("content-type", body.contentType()));
        httpRequest.timeout(requestTimeout);

        // TODO(rfink): Think about repeatability/retries
        CompletableFuture<Response> future = client.sendAsync(
                        httpRequest.build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(this::toResponse);

        return new CompletableToListenableFuture<>(future);
    }

    private static HttpRequest.Builder newRequestBuilder(URL url) {
        try {
            return HttpRequest.newBuilder().uri(url.toURI());
        } catch (URISyntaxException e) {
            throw new SafeRuntimeException("Failed to construct URI, this is a bug", e);
        }
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
            public ListMultimap<String, String> headers() {
                ListMultimap<String, String> headers = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                        .arrayListValues()
                        .build();
                response.headers().map().forEach(headers::putAll);
                return headers;
            }

            @Override
            public void close() {
                try {
                    body().close();
                } catch (IOException e) {
                    log.warn("Failed to close response", e);
                }
            }
        };
    }

    private static HttpRequest.BodyPublisher toBody(Request request) {
        if (request.body().isPresent()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try {
                request.body().get().writeTo(bytes);
            } catch (IOException e) {
                throw new SafeRuntimeException("Failed to create a BodyPublisher", e);
            }
            return HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray());
        } else {
            return HttpRequest.BodyPublishers.noBody();
        }
    }
}
