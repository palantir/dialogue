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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;

public final class OkHttpChannel implements Channel {

    private final OkHttpClient client;
    private final URL baseUrl;
    private final OkHttpCallback.Factory callbackFactory;

    private OkHttpChannel(
            OkHttpClient client,
            URL baseUrl,
            OkHttpCallback.Factory callbackFactory) {
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
        this.callbackFactory = callbackFactory;
    }

    public static OkHttpChannel of(OkHttpClient client, URL baseUrl, ErrorDecoder errorDecoder) {
        return new OkHttpChannel(client, baseUrl, observer -> new OkHttpCallback(observer, errorDecoder));
    }

    @VisibleForTesting
    static OkHttpChannel of(OkHttpClient client, URL baseUrl, OkHttpCallback.Factory callbackFactory) {
        return new OkHttpChannel(client, baseUrl, callbackFactory);
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
    public Call createCall(Endpoint endpoint, Request request) {
        // Create base request given the URL
        String endpointPath = endpoint.renderPath(request.pathParams());
        Preconditions.checkArgument(endpointPath.startsWith("/"), "endpoint path must start with /");
        // Concatenation is OK since base path is empty or starts with / and does not end with / ,
        // and endpoint path starts with /
        String effectivePath = baseUrl.getPath() + endpointPath;

        HttpUrl.Builder url = new HttpUrl.Builder()
                .scheme(baseUrl.getProtocol())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort())
                .encodedPath(effectivePath);
        request.queryParams().entries().forEach(entry -> url.addQueryParameter(entry.getKey(), entry.getValue()));
        okhttp3.Request.Builder okRequest = new okhttp3.Request.Builder().url(url.build());

        // Fill request body and set HTTP method
        switch (endpoint.httpMethod()) {
            case GET:
                Preconditions.checkArgument(!request.body().isPresent(), "GET endpoints must not have a request body");
                okRequest = okRequest.get();
                break;
            case POST:
                okRequest = okRequest.post(toOkHttpBody(
                        request.body().orElseThrow(() ->
                                new SafeIllegalArgumentException("POST endpoints must have a request body"))));
                break;
            case PUT:
                okRequest = okRequest.put(toOkHttpBody(
                        request.body().orElseThrow(()
                                -> new SafeIllegalArgumentException("PUT endpoints must have a request body"))));
                break;
            case DELETE:
                okRequest = okRequest.delete(request.body().isPresent() ? toOkHttpBody(request.body().get()) : null);
                break;
        }

        // Fill headers
        for (Map.Entry<String, String> header : request.headerParams().entrySet()) {
            okRequest.header(header.getKey(), header.getValue());
        }

        // Create Dialogue call that delegates to an OkHttp Call.
        okhttp3.Call okCall = client.newCall(okRequest.build());
        return new Call() {
            @Override
            public void execute(Observer observer) {
                Preconditions.checkState(!okCall.isExecuted(), "Calls must only be executed once.");
                okCall.enqueue(callbackFactory.create(observer));
            }

            @Override
            public void cancel() {
                okCall.cancel();
            }
        };
    }
}
