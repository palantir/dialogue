/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations;

import com.palantir.conjure.java.dialogue.serde.core.ErrorDecoder;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import java.util.Optional;

public final class ErrorHandlingDeserializerFactory<T> implements DeserializerFactory<T> {

    private final DeserializerFactory<T> delegate;
    private final ErrorDecoder errorDecoder;

    public ErrorHandlingDeserializerFactory(DeserializerFactory<T> delegate, ErrorDecoder errorDecoder) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate");
        this.errorDecoder = Preconditions.checkNotNull(errorDecoder, "errorDecoder");
    }

    @Override
    public <T1 extends T> Deserializer<T1> deserializerFor(TypeMarker<T1> type) {
        Deserializer<T1> delegateDeserializer = delegate.deserializerFor(type);
        return new Deserializer<T1>() {
            @Override
            public T1 deserialize(Response response) {
                boolean closeResponse = true;
                try {
                    if (errorDecoder.isError(response)) {
                        throw errorDecoder.decode(response);
                    } else {
                        // delegate has taken on responsibility for closing the response body
                        closeResponse = false;
                        return delegateDeserializer.deserialize(response);
                    }
                } finally {
                    if (closeResponse) {
                        response.close();
                    }
                }
            }

            @Override
            public Optional<String> accepts() {
                return delegateDeserializer.accepts();
            }
        };
    }
}
