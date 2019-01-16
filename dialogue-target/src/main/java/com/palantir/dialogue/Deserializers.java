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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Verify;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Deserializers {

    private Deserializers() {}

    /**
     * Returns a {@link Deserializer} that returns the given stream as verbatim UTF8 string. In contrast to a {@link
     * #jackson Jackson deserializer} for type {@code String}, this deserializer does not expect the given string to be
     * a JSON object, i.e., wrapped in quotes. For instance, the input stream carrying three bytes {@code abc} gets
     * deserialized the the three-byte Java string {@code abc}, whereas the corresponding Jackson deserializer
     * deserializes the five-byte stream {@code "abc"} into the three-byte Java string {@code abc}.
     */
    public static Deserializer<String> string(String endpointName) {
        return stream -> {
            try {
                Verify.verifyNotNull(stream, "%s expects non-null response stream", endpointName);
                return new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Failed to deserialize response stream to String in method %s", endpointName), e);
            }
        };
    }

    /** Performs no deserialization and returns the given {@link InputStream}. */
    public static Deserializer<InputStream> passthrough() {
        return stream -> stream;
    }

    /**
     * Creates a new {@link Deserializer} that attempts to deserialize a given {@link InputStream} into an object of
     * type {@link T} using the given Jackson {@link ObjectMapper}.
     */
    public static <T> Deserializer<T> jackson(String endpointName, ObjectMapper mapper, TypeReference<T> type) {
        return stream -> {
            Verify.verifyNotNull(stream, "%s expects non-null response stream", endpointName);
            try {
                return mapper.readValue(stream, type);
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Failed to deserialize response stream to %s in method %s",
                                type.getType(), endpointName),
                        e);
            }
        };
    }

    /**
     * Creates a new {@link Deserializer} expects a null or empty {@link InputStream} and always returns {@link
     * Void}-typed null.
     */
    public static Deserializer<Void> empty(String endpointName) {
        return stream -> {
            try {
                Verify.verify(stream == null || stream.read() == -1,
                        "%s expects null or empty response stream", endpointName);
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Failed to consume response stream in method %s", endpointName), e);
            }
            return null;
        };
    }
}
