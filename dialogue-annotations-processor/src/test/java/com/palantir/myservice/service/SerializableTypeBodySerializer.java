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

package com.palantir.myservice.service;

import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Serializer;
import java.io.OutputStream;

public final class SerializableTypeBodySerializer implements Serializer<SerializableType> {
    @Override
    public RequestBody serialize(SerializableType _value) {
        return new RequestBody() {
            @Override
            public void writeTo(OutputStream _output) {}

            @Override
            public String contentType() {
                return "application/json";
            }

            @Override
            public boolean repeatable() {
                return true;
            }

            @Override
            public void close() {}
        };
    }
}
