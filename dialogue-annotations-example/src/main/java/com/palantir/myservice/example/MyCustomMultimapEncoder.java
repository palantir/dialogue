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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.palantir.dialogue.annotations.MultimapParamEncoder;

public class MyCustomMultimapEncoder implements MultimapParamEncoder<MyCustomParamType> {
    @Override
    public Multimap<String, String> toParamValues(MyCustomParamType value) {
        return ImmutableMultimap.of("value-from-multimap", value.value());
    }
}
