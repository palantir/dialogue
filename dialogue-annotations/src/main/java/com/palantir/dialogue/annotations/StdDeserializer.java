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
import com.palantir.dialogue.TypeMarker;
import java.util.Optional;

@SuppressWarnings({"RawTypes", "unchecked"})
public abstract class StdDeserializer<T> implements DeserializerFactory, Deserializer<T> {

    private final Optional<String> accepts;

    public StdDeserializer(String accepts) {
        this.accepts = Optional.of(accepts);
    }

    @Override
    public final Optional<String> accepts() {
        return accepts;
    }

    @Override
    public final <T1> Deserializer<T1> deserializerFor(TypeMarker<T1> _type) {
        return (Deserializer) this;
    }
}
