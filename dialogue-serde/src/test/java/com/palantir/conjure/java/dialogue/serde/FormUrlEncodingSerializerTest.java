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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import com.palantir.dialogue.RequestBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormUrlEncodingSerializerTest {

    @Test
    void basic_key_value_pairs() throws IOException {
        RequestBody body = FormUrlEncodingSerializer.INSTANCE.serialize(
                ImmutableMap.of("token", "foo", "TOKEN2", "bar", "", "", "empty", ""));
        assertThat(asString(body)).isEqualTo("token=foo&TOKEN2=bar&=&empty=");
    }

    @Test
    void percent_encodes_some_common_special_characters() throws IOException {
        RequestBody body = FormUrlEncodingSerializer.INSTANCE.serialize(
                ImmutableMap.of("key!@#$%^&*()_+-= ", "value!@#$%^&*()_+-= "));
        assertThat(asString(body))
                .isEqualTo("key%21%40%23%24%25%5E%26*%28%29_%2B-%3D+=value%21%40%23%24%25%5E%26*%28%29_%2B-%3D+");
    }

    @Test
    void percent_encodes_silly_characters() throws IOException {
        RequestBody body = FormUrlEncodingSerializer.INSTANCE.serialize(ImmutableMap.of("key", sillyBytes(50)));
        assertThat(asString(body))
                .isEqualTo("key=%00%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F%10%11%12%13%14%15"
                        + "%16%17%18%19%1A%1B%1C%1D%1E%1F+%21%22%23%24%25%26%27%28%29*%2B%2C-.%2F01");
    }

    @Test
    void percent_emoji() throws IOException {
        RequestBody body = FormUrlEncodingSerializer.INSTANCE.serialize(ImmutableMap.of("key", "🚀"));
        assertThat(asString(body)).isEqualTo("key=%F0%9F%9A%80");
    }

    @Test
    void handles_null() throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put("foo", null);
        RequestBody body = FormUrlEncodingSerializer.INSTANCE.serialize(map);
        assertThatThrownBy(() -> asString(body)).hasMessage("value must not be null: {key=foo}");
    }

    private static String sillyBytes(int count) {
        byte[] silly = new byte[count];
        for (int i = 0; i < silly.length; i++) {
            silly[i] = (byte) i;
        }
        return new String(silly, StandardCharsets.UTF_8);
    }

    private static String asString(RequestBody body) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}
