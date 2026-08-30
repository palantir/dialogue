/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.myservice.example;

import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.annotations.SerializerFactory;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class MyBodyTypeSerializerFactory<T> implements SerializerFactory<MyBodyType<T>> {

    private final ConjureRuntime runtime;

    public MyBodyTypeSerializerFactory(ConjureRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends MyBodyType<T>> Serializer<U> serializerFor(TypeMarker<U> type) {
        Type innerType = ((ParameterizedType) type.getType()).getActualTypeArguments()[0];

        Serializer<T> serializer = runtime.bodySerDe().serializer((TypeMarker<T>) TypeMarker.of(innerType));

        return new Serializer<U>() {
            @Override
            public RequestBody serialize(U value) {
                return serializer.serialize(value.value());
            }
        };
    }
}
