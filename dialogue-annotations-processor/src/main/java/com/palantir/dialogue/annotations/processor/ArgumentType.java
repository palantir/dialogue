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

package com.palantir.dialogue.annotations.processor;

import com.squareup.javapoet.TypeName;
import org.derive4j.Data;

@Data
public interface ArgumentType {
    interface Cases<R> {
        /** Should be handled by {@link com.palantir.dialogue.PlainSerDe}. */
        R primitive(TypeName javaTypeName, String planSerDeMethodName);

        R rawRequestBody(TypeName requestBodyTypeName);

        R customType(TypeName customTypeName, String valueOfMethodName);
    }

    <R> R match(ArgumentType.Cases<R> cases);
}
