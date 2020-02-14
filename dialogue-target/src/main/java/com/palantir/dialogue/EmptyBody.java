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

import java.io.OutputStream;
import java.util.OptionalLong;

/** A singleton object that always serializes to an empty byte array. */
public enum EmptyBody {
    INSTANCE;

    public static Serializer<EmptyBody> serializer(String contentType) {
        return value -> new RequestBody() {
            @Override
            public OptionalLong length() {
                return OptionalLong.of(0L);
            }

            @Override
            public void writeTo(OutputStream _output) {}

            @Override
            public String contentType() {
                return contentType;
            }
        };
    }
}
