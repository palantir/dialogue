/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.dialogue.serde;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.palantir.conjure.java.dialogue.serde.Encoding.Deserializer;
import com.palantir.dialogue.TypeMarker;
import java.lang.reflect.Type;

/**
 * Deserializers for Conjure error types, shared by the error decoders of a {@link ConjureBodySerDe}. Each decoder
 * holds a deserializer for every error it handles, but a deserializer's reader is only built when an error of that
 * type is first decoded. Most endpoints never see most of their errors.
 */
final class ErrorDeserializers {
    // Keyed by Type rather than TypeMarker: anonymous TypeMarker subclasses retain the client that created them.
    // Weak values keep a deserializer shared for as long as any decoder holds it. There is no size bound, because
    // evicting a deserializer that decoders still hold would only stop it from being shared.
    private final LoadingCache<Type, Deserializer<?>> deserializers;

    ErrorDeserializers() {
        // Errors are always JSON, see ExceptionDeserializingErrorDecoder.
        this(Encodings.json());
    }

    @VisibleForTesting
    ErrorDeserializers(Encoding encoding) {
        Encoding lazyEncoding = new LazilyInitializedEncoding(encoding);
        this.deserializers =
                Caffeine.newBuilder().weakValues().build(type -> lazyEncoding.deserializer(TypeMarker.of(type)));
    }

    @SuppressWarnings("unchecked")
    <T> Deserializer<T> deserializer(TypeMarker<T> errorType) {
        return (Deserializer<T>) deserializers.get(errorType.getType());
    }
}
