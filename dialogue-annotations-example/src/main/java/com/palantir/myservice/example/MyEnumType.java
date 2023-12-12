/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

public final class MyEnumType {

    public static final MyEnumType VALUE_1 = new MyEnumType(Value.VALUE_1, "VALUE_1");
    public static final MyEnumType VALUE_2 = new MyEnumType(Value.VALUE_2, "VALUE_2");
    private final Value value;
    private final String string;

    private MyEnumType(Value value, String string) {
        this.value = value;
        this.string = string;
    }

    public Value get() {
        return value;
    }

    @Override
    public String toString() {
        return string;
    }

    public enum Value {
        VALUE_1,
        VALUE_2
    }
}
