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

import com.palantir.dialogue.annotations.ListParamEncoder;
import com.palantir.dialogue.annotations.ParamEncoder;
import java.util.Collections;
import java.util.List;

public final class MyCustomParamTypeParameterEncoder
        implements ParamEncoder<MyCustomParamType>, ListParamEncoder<MyCustomParamType> {
    @Override
    public String toParamValue(MyCustomParamType value) {
        return value.value();
    }

    @Override
    public List<String> toParamValues(MyCustomParamType value) {
        return Collections.singletonList(value.value());
    }
}
