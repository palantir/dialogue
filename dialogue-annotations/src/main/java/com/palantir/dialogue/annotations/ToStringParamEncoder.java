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

import java.util.List;
import java.util.Objects;

/**
 * Simple Encoder implementation which always uses the {@link Object#toString()} value.
 * This is not used by <i>default</i> because it should be a conscious decision to rely on
 * {@link Object#toString()} over the wire.
 */
public final class ToStringParamEncoder implements ListParamEncoder<Object>, ParamEncoder<Object> {

    public ToStringParamEncoder() {}

    @Override
    public List<String> toParamValues(Object value) {
        return value == null ? List.of() : List.of(toParamValue(value));
    }

    @Override
    public String toParamValue(Object value) {
        return Objects.toString(value);
    }
}
