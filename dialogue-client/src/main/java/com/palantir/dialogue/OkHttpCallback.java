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
import java.io.InputStream;
import javax.annotation.Nonnull;
import okhttp3.Callback;
import okhttp3.ResponseBody;

/**
 * An adapter between OkHttp {@link Callback}s and Dialogue {@link Observer}s for a given Dialogue {@link Endpoint}.
 * <p>
 * A {@link okhttp3.Response#isSuccessful() successful} response is treated as follows: the response body is
 * deserialized into an object of type {@link RespT} using the endpoint's {@link Endpoint#responseDeserializer()
 * response deserializer} and the result is passed to {@link Observer#success}. An absent/null response body is passed
 * ot the deserializer as a null {@link InputStream}.
 * <p>
 * A non-successful {@link okhttp3.Response}, i.e., one with {@code response.isSuccessful() == false}, is converted to a
 * {@link com.palantir.conjure.java.api.errors.RemoteException} using the endpoint's {@link Endpoint#errorDecoder} and
 * presented to {@link Observer#failure}.
 * <p>
 * Any other failure condition, e.g., OkHttp connection-level errors, deserialization errors, etc., are presented to
 * {@link Observer#exception}.
 * <p>
 * Implementations must be thread-safe.
 */
class OkHttpCallback<ReqT, RespT> implements Callback {

    private final Endpoint<ReqT, RespT> endpoint;
    private final Observer<RespT> observer;

    OkHttpCallback(Endpoint<ReqT, RespT> endpoint, Observer<RespT> observer) {
        this.endpoint = endpoint;
        this.observer = observer;
    }

    /**
     * Supplies an {@link OkHttpCallback} instance for a given endpoint. Implementations may choose to not create new
     * instances for every call and may, for example, supply cache instances by endpoint.
     */
    interface Factory {
        <ReqT, RespT> OkHttpCallback<ReqT, RespT> create(Endpoint<ReqT, RespT> endpoint, Observer<RespT> observer);
    }

    @Override
    public void onFailure(@Nonnull okhttp3.Call call, @Nonnull IOException error) {
        observer.exception(error);
    }

    @Override
    public void onResponse(@Nonnull okhttp3.Call call, @Nonnull okhttp3.Response response) {
        try {
            // TODO(rfink): 204 responses for Optional types [or aliases thereof] should yield Optional#absent
            if (response.isSuccessful()) {
                // TODO(rfink): How are HEAD requests or change protocol responses handled?
                ResponseBody body = response.body();
                InputStream stream = body == null ? null : body.byteStream();
                observer.success(endpoint.responseDeserializer().deserialize(stream));
            } else {
                observer.failure(endpoint.errorDecoder().decode(response));
            }
        } catch (Throwable t) {
            observer.exception(t);
        }
    }
}
