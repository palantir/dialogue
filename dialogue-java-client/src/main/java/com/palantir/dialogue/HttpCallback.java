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

import com.google.common.util.concurrent.FutureCallback;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An adapter between a {@link FutureCallback callback} for a {@link HttpResponse} and a Dialogue {@link Observer}.
 * <p>
 * A successful response (i.e., one with status code in [200, 300)) is treated as follows: the response body is
 * deserialized into an object of type {@link Response} using the given {@link ErrorDecoder} and the result is passed to
 * {@link Observer#success}.
 * <p>
 * Any other response is considered non-successful and is converted to
 * a {@link com.palantir.conjure.java.api.errors.RemoteException} using the given
 * {@link ErrorDecoder error decoder} and presented to {@link Observer#failure}.
 * <p>
 * Any other failure condition, e.g., connection-level errors, deserialization errors, etc., are presented to
 * {@link Observer#exception}.
 */
final class HttpCallback implements FutureCallback<HttpResponse<InputStream>> {

    private final Observer observer;
    private final ErrorDecoder errorDecoder;

    HttpCallback(Observer observer, ErrorDecoder errorDecoder) {
        this.observer = observer;
        this.errorDecoder = errorDecoder;
    }

    @Override
    public void onSuccess(@Nullable HttpResponse<InputStream> result) {
        try {
            Response response = toResponse(result);
            if (isSuccessful(response.code())) {
                observer.success(response);
            } else {
                observer.failure(errorDecoder.decode(response));
            }
        } catch (Throwable t) {
            observer.exception(t);
        }
    }

    @Override
    public void onFailure(Throwable throwable) {
        observer.exception(throwable);
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
                return response.headers().firstValue(Headers.CONTENT_TYPE);
            }
        };
    }
}
