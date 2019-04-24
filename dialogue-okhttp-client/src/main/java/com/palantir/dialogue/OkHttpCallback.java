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

/**
 * TODO(rfink): Update docs
 * <p>
 * An adapter between OkHttp {@link Callback}s and Dialogue {@link Observer}s for a given Dialogue {@link Endpoint}.
 * <p>
 * A {@link okhttp3.Response#isSuccessful() successful} response is treated as follows: the response body is
 * deserialized into an object of type {@link Response} using the given {@link Deserializer
 * response deserializer} and the result is passed to {@link Observer#success}. An absent/null response body is passed
 * ot the deserializer as a null {@link InputStream}.
 * <p>
 * A non-successful {@link okhttp3.Response}, i.e., one with {@code response.isSuccessful() == false}, is converted to
 * a {@link com.palantir.conjure.java.api.errors.RemoteException} using the given
 * {@link ErrorDecoder error decoder} and presented to {@link Observer#failure}.
 * <p>
 * Any other failure condition, e.g., OkHttp connection-level errors, deserialization errors, etc., are presented to
 * {@link Observer#exception}.
 * <p>
 * Implementations must be thread-safe.
 */
class OkHttpCallback implements Callback {

    private final Observer observer;
    private final ErrorDecoder errorDecoder;

    OkHttpCallback(Observer observer, ErrorDecoder errorDecoder) {
        this.observer = observer;
        this.errorDecoder = errorDecoder;
    }

    /**
     * Supplies an {@link OkHttpCallback} instance for a given endpoint. Implementations may choose to not create new
     * instances for every call and may, for example, supply cache instances by endpoint.
     */
    interface Factory {
        // TODO(rfink): Remove this, it's not needed anymore
        OkHttpCallback create(Observer observer);
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
                observer.success(OkHttpResponse.wrap(response));
            } else {
                // TODO(rfink): It's asymmetric that we deserialize errors here, but not payloads
                observer.failure(errorDecoder.decode(OkHttpResponse.wrap(response)));
            }
        } catch (Throwable t) {
            observer.exception(t);
        }
    }
}
