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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.palantir.dialogue.annotations.ParameterSerializer;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public final class ArgumentTypesResolverTest {

    @Test
    public void testConsistency() {
        ImmutableMap.Builder<TypeName, String> builder = ImmutableMap.builder();
        for (Method method : ParameterSerializer.class.getDeclaredMethods()) {
            Preconditions.checkArgument(
                    method.getReturnType().equals(String.class),
                    "Return type is not String",
                    SafeArg.of("method", method));
            Preconditions.checkArgument(
                    method.getParameterCount() == 1,
                    "Serializer methods should have a single " + "arg",
                    SafeArg.of("method", method));

            ClassName parameterType = ClassName.get(Primitives.wrap(method.getParameterTypes()[0]));
            builder.put(parameterType, method.getName());
        }

        assertThat(builder.buildOrThrow())
                .as("the map in %s is missing some values", ArgumentTypesResolver.class)
                .containsAllEntriesOf(ArgumentTypesResolver.PARAMETER_SERIALIZER_TYPES);
    }
}
