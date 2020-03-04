/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.io.InputStream;

/**
 * Package-private internal api.
 * This partial Encoding implementation exists to allow binary responses to share the same safety
 * and validation provided by structured encodings. This is only consumed internally to create
 * a binary-specific <pre>EncodingDeserializerRegistry</pre>.
 */
enum BinaryEncoding implements Encoding {
    INSTANCE;

    static final String CONTENT_TYPE = "application/octet-stream";
    static final TypeMarker<InputStream> MARKER = new TypeMarker<InputStream>() {};

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> _type) {
        throw new UnsupportedOperationException("BinaryEncoding does not support serializers");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        Preconditions.checkArgument(
                InputStream.class.equals(type.getType()),
                "BinaryEncoding only supports InputStream",
                SafeArg.of("requested", type));
        return input -> (T) input;
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean supportsContentType(String contentType) {
        return Encodings.matchesContentType(CONTENT_TYPE, contentType);
    }
}
