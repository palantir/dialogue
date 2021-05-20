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

import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.dialogue.serde.Encoding;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import java.util.Arrays;

public abstract class StdEncoding implements DeserializerFactory<Object>, SerializerFactory<Object> {

    private final BodySerDe bodySerDe;

    public StdEncoding(Encoding... encodings) {
        Preconditions.checkNotNull(encodings, "encodings cannot be null");
        Preconditions.checkArgument(encodings.length > 0, "encodings cannot be empty");
        DefaultConjureRuntime.Builder builder = DefaultConjureRuntime.builder();
        Arrays.stream(encodings).forEach(builder::encodings);
        bodySerDe = builder.build().bodySerDe();
    }

    @Override
    public final <T> com.palantir.dialogue.Deserializer<T> deserializerFor(TypeMarker<T> type) {
        return bodySerDe.deserializer(type);
    }

    @Override
    public final <T> com.palantir.dialogue.Serializer<T> serializerFor(TypeMarker<T> type) {
        return bodySerDe.serializer(type);
    }
}
