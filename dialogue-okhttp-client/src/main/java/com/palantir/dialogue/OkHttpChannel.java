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
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import javax.annotation.Nullable;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;

public final class OkHttpChannel implements Channel {

    private final OkHttpClient client;
    private final UrlBuilder baseUrl;
    private final ErrorDecoder errorDecoder;

    private OkHttpChannel(OkHttpClient client, URL baseUrl, ErrorDecoder errorDecoder) {
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
        this.errorDecoder = errorDecoder;
    }

    /** Creates a new channel with the given underlying client, baseUrl, and error decoder. Note that */
    public static OkHttpChannel of(OkHttpClient client, URL baseUrl, ErrorDecoder errorDecoder) {
        return new OkHttpChannel(client, baseUrl, errorDecoder);
    }

    private RequestBody toOkHttpBody(com.palantir.dialogue.RequestBody body) {
        return new RequestBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return MediaType.parse(body.contentType());
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                ByteStreams.copy(body.content(), sink.outputStream());
            }
        };
    }

    @Override
    public ListenableFuture<Response> createCall(Endpoint endpoint, Request request) {
        // Create base request given the URL
        UrlBuilder url = baseUrl.newBuilder();
        endpoint.renderPath(request.pathParams(), url);
        request.queryParams().entries().forEach(entry -> url.queryParam(entry.getKey(), entry.getValue()));
        okhttp3.Request.Builder okRequest = new okhttp3.Request.Builder().url(url.build());

        // Fill request body and set HTTP method
        switch (endpoint.httpMethod()) {
            case GET:
                Preconditions.checkArgument(!request.body().isPresent(), "GET endpoints must not have a request body");
                okRequest = okRequest.get();
                break;
            case POST:
                okRequest = okRequest.post(toOkHttpBody(
                        request.body().orElseThrow(() -> new SafeIllegalArgumentException(
                                "Endpoint must have a request body", SafeArg.of("method", "POST")))));
                break;
            case PUT:
                okRequest = okRequest.put(toOkHttpBody(
                        request.body().orElseThrow(() -> new SafeIllegalArgumentException(
                                "Endpoint must have a request body", SafeArg.of("method", "PUT")))));
                break;
            case DELETE:
                Preconditions.checkArgument(
                        !request.body().isPresent(), "DELETE endpoints must not have a request body");
                okRequest = okRequest.delete(request.body().isPresent() ? toOkHttpBody(request.body().get()) : null);
                break;
        }

        // Fill headers
        for (Map.Entry<String, String> header : request.headerParams().entrySet()) {
            okRequest.header(header.getKey(), header.getValue());
        }

        // TODO(rfink): Think about repeatability/retries

        okhttp3.Call okCall = client.newCall(okRequest.build());

        SettableFuture<Response> future = SettableFuture.create();
        okCall.enqueue(new Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                future.setException(e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                future.set(OkHttpResponse.wrap(response));
            }
        });
        return future;
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
