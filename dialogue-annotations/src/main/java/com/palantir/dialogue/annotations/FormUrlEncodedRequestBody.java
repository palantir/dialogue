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

import static com.palantir.logsafe.Preconditions.checkArgument;

import com.palantir.dialogue.RequestBody;
import com.palantir.logsafe.UnsafeArg;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class FormUrlEncodedRequestBody implements RequestBody {
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private final Map<String, String> map;

    FormUrlEncodedRequestBody(Map<String, String> map) {
        this.map = map;
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (first) {
                first = false;
            } else {
                output.write('&');
            }

            checkArgument(entry.getKey() != null, "key must not be null");
            String key = URLEncoder.encode(entry.getKey(), UTF_8);
            output.write(key.getBytes(StandardCharsets.UTF_8));
            output.write('=');

            checkArgument(entry.getValue() != null, "value must not be null", UnsafeArg.of("key", entry.getKey()));
            String value = URLEncoder.encode(entry.getValue(), UTF_8);
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public String contentType() {
        return "application/x-www-form-urlencoded";
    }

    @Override
    public boolean repeatable() {
        return true;
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "FormUrlEncodedRequestBody{map.keySet=" + map.keySet() + '}';
    }
}
