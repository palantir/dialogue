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

package com.palantir.dialogue.annotations.processor.data;

import com.squareup.javapoet.TypeName;
import java.util.Optional;
import org.derive4j.Data;

@Data
public interface ParameterType {
    interface Cases<R> {

        R rawBody();

        R body(TypeName serializerFactory, String serializerFieldName);

        R header(String headerName, Optional<ParameterEncoderType> paramEncoderType);

        R headerMap(ParameterEncoderType parameterEncoderType);

        R path(Optional<ParameterEncoderType> paramEncoderType);

        R query(String paramName, Optional<ParameterEncoderType> paramEncoderType);

        R queryMap(ParameterEncoderType parameterEncoderType);
    }

    <R> R match(Cases<R> cases);
}
