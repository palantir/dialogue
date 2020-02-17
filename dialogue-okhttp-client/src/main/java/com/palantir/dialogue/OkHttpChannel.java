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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import javax.annotation.Nullable;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;

public final class OkHttpChannel implements Channel {

    private static final RequestBody EMPTY_REQUEST_BODY = RequestBody.create(null, new byte[] {});
    private final OkHttpClient client;
    private final UrlBuilder baseUrl;

    private OkHttpChannel(OkHttpClient client, URL baseUrl) {
        this.client = client;
        this.baseUrl = UrlBuilder.from(baseUrl);
    }

    /** Creates a new channel with the given underlying client, baseUrl, and error decoder. Note that */
    public static OkHttpChannel of(OkHttpClient client, URL baseUrl) {
        return new OkHttpChannel(client, baseUrl);
    }

    private RequestBody toOkHttpBody(Optional<com.palantir.dialogue.RequestBody> body) {
        return body.map(this::toOkHttpBody).orElse(EMPTY_REQUEST_BODY);
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
                body.writeTo(sink.outputStream());
            }
        };
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
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
                okRequest = okRequest.post(toOkHttpBody(request.body()));
                break;
            case PUT:
                okRequest = okRequest.put(toOkHttpBody(request.body()));
                break;
            case DELETE:
                okRequest = okRequest.delete(
                        request.body().isPresent() ? toOkHttpBody(request.body().get()) : null);
                break;
        }

        // Fill headers
        request.headerParams().forEach(okRequest::addHeader);

        // TODO(rfink): Think about repeatability/retries

        okhttp3.Call okCall = client.newCall(okRequest.build());

        SettableFuture<Response> future = SettableFuture.create();
        okCall.enqueue(new Callback() {
            @Override
            public void onFailure(okhttp3.Call _call, IOException exception) {
                future.setException(exception);
            }

            @Override
            public void onResponse(okhttp3.Call _call, okhttp3.Response response) {
                future.set(OkHttpResponse.wrap(response));
            }
        });
        return future;
    }
}
