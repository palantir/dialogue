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

package com.palantir.myservice.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.annotations.Json;
import com.palantir.dialogue.annotations.StdSerializer;

public final class MySerializableTypeBodySerializer extends StdSerializer<MySerializableType> {

    private static final Serializer<MySerializableType> SERIALIZER = new Json(
                    new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT))
            .serializerFor(new TypeMarker<>() {});

    @Override
    public RequestBody serialize(MySerializableType value) {
        return SERIALIZER.serialize(value);
    }
}
