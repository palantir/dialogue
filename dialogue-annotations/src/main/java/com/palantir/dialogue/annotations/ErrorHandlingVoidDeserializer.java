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

import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.Optional;

public final class ErrorHandlingVoidDeserializer implements Deserializer<Void> {

    private final Deserializer<Void> delegate;
    private final ErrorDecoder errorDecoder;

    public ErrorHandlingVoidDeserializer(Deserializer<Void> delegate, ErrorDecoder errorDecoder) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate");
        this.errorDecoder = Preconditions.checkNotNull(errorDecoder, "errorDecoder");
    }

    @Override
    public Void deserialize(Response response) {
        try (response) {
            if (errorDecoder.isError(response)) {
                throw errorDecoder.decode(response);
            } else {
                return delegate.deserialize(response);
            }
        }
    }

    @Override
    public Optional<String> accepts() {
        return delegate.accepts();
    }
}
