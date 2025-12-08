/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.annotations.StdDeserializer;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import javax.annotation.Nullable;

public final class CustomRuntimeAwareDeserializer extends StdDeserializer<MySerializableType> {

    private @Nullable final ConjureRuntime runtime;

    public CustomRuntimeAwareDeserializer(ConjureRuntime runtime) {
        super("application/json");
        this.runtime = runtime;
    }

    // This constructor should not be used by the generated code. It should detect the one above, and use that instead.
    public CustomRuntimeAwareDeserializer() {
        super("application/json");
        runtime = null;
    }

    @Override
    public MySerializableType deserialize(Response response) {
        if (runtime == null) {
            throw new SafeIllegalStateException(
                    "Wrong constructor for CustomRuntimeAwareDeserializer used in generated code.");
        }
        Deserializer<SafeLong> deserializer = runtime.bodySerDe().deserializer(new TypeMarker<>() {});
        SafeLong longValue = deserializer.deserialize(response);
        return ImmutableMySerializableType.of("CUSTOM-" + longValue);
    }
}
