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

import java.io.IOException;
import javax.annotation.Nonnull;
import okhttp3.Callback;

/**
 * An adapter between OkHttp {@link Callback}s and a Dialogue {@link Observer}.
 * <p>
 * A {@link okhttp3.Response#isSuccessful() successful} response is treated as follows: the response body is
 * deserialized into an object of type {@link Response} using the given {@link ErrorDecoder} and the result is passed to
 * {@link Observer#success}.
 * <p>
 * A non-successful {@link okhttp3.Response}, i.e., one with {@code response.isSuccessful() == false}, is converted to
 * a {@link com.palantir.conjure.java.api.errors.RemoteException} using the given
 * {@link ErrorDecoder error decoder} and presented to {@link Observer#failure}.
 * <p>
 * Any other failure condition, e.g., OkHttp connection-level errors, deserialization errors, etc., are presented to
 * {@link Observer#exception}.
 */
class OkHttpCallback implements Callback {

    private final Observer observer;
    private final ErrorDecoder errorDecoder;

    OkHttpCallback(Observer observer, ErrorDecoder errorDecoder) {
        this.observer = observer;
        this.errorDecoder = errorDecoder;
    }

    @Override
    public void onFailure(@Nonnull okhttp3.Call call, @Nonnull IOException error) {
        observer.exception(error);
    }

    @Override
    public void onResponse(@Nonnull okhttp3.Call call, @Nonnull okhttp3.Response response) {
        try {
            if (response.isSuccessful()) {
                observer.success(OkHttpResponse.wrap(response));
            } else {
                observer.failure(errorDecoder.decode(OkHttpResponse.wrap(response)));
            }
        } catch (Throwable t) {
            observer.exception(t);
        }
    }
}
