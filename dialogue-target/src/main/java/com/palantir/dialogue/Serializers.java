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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Verify;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// TODO(rfink): This class lacks test coverage
public final class Serializers {

    private Serializers() {}

    /**
     * Creates a new {@link Serializer} serializes a given {@link T type-T object} into its JSON representation using
     * the given Jackson {@link ObjectMapper}.
     */
    public static <T> Serializer<T> jackson(String endpointName, ObjectMapper mapper) {
        return value -> {
            Verify.verifyNotNull(value, "%s expects non-null request object", endpointName);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                mapper.writeValue(bytes, value);
                return bytes.toByteArray();
            } catch (IOException e) {
                throw new SafeRuntimeException(
                        "Failed to serialize object of type %s in method %s",
                        e,
                        SafeArg.of("type", value.getClass().getSimpleName()),
                        SafeArg.of("endpoint", endpointName));
            }
        };
    }

    /** Returns a serializer that fails whenever it gets invoked. */
    public static Serializer<Void> failing() {
        return value -> {
            throw new SafeRuntimeException("Did not expect a call to AlwaysFailingSerializer");
        };
    }
}
