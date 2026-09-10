/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.palantir.dialogue.RequestBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class FormUrlEncodedSerializerFactoryTest {

    public static final class MapFormUrlEncodedSerializer extends FormUrlEncodedSerializerFactory<Map<String, String>> {
        @Override
        protected Map<String, String> extractFields(Map<String, String> value) {
            return value;
        }
    }

    @Test
    public void basic_key_value_pairs() throws IOException {
        RequestBody body = serialize(ImmutableMap.of("token", "foo", "TOKEN2", "bar", "", "", "empty", ""));
        assertThat(asString(body)).isEqualTo("token=foo&TOKEN2=bar&=&empty=");
    }

    @Test
    public void percent_encodes_some_common_special_characters() throws IOException {
        RequestBody body = serialize(ImmutableMap.of("key!@#$%^&*()_+-= ", "value!@#$%^&*()_+-=" + " "));
        assertThat(asString(body))
                .isEqualTo("key%21%40%23%24%25%5E%26*%28%29_%2B-%3D+=value%21%40%23%24%25%5E%26*%28%29_%2B-%3D+");
    }

    private RequestBody serialize(Map<String, String> values) {
        return new MapFormUrlEncodedSerializer().serialize(values);
    }

    private static String asString(RequestBody body) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);
        return baos.toString(StandardCharsets.UTF_8.name());
    }
}
