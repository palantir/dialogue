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

import com.google.common.base.CharMatcher;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Serializer;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

enum FormUrlEncodingSerializer implements Serializer<Map<String, String>> {
    INSTANCE;

    @Override
    public RequestBody serialize(Map<String, String> value) {
        return new RequestBody() {
            @Override
            public void writeTo(OutputStream output) throws IOException {
                boolean first = true;
                for (Map.Entry<String, String> entry : value.entrySet()) {
                    if (first) {
                        first = false;
                    } else {
                        output.write('&');
                    }

                    String key = Preconditions.checkNotNull(entry.getKey(), "key must not be null");
                    output.write(FormUrlEncoder.encode(key).getBytes(StandardCharsets.UTF_8));
                    output.write('=');

                    String value = Preconditions.checkNotNull(
                            entry.getValue(), "value must not be null", UnsafeArg.of("key", key));
                    output.write(FormUrlEncoder.encode(value).getBytes(StandardCharsets.UTF_8));
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
        };
    }

    /** As per https://url.spec.whatwg.org/#urlencoded-serializing. */
    private static class FormUrlEncoder {
        private static final CharMatcher DIGIT = CharMatcher.inRange('0', '9');
        private static final CharMatcher ALPHA = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'));
        private static final CharMatcher PRESERVE = DIGIT.or(ALPHA).or(CharMatcher.anyOf("*-._"));

        // percent-encodes every byte in the source string with it's percent-encoded representation, except for
        // bytes that (in their unsigned char sense) are matched by charactersToKeep
        static String encode(String source) {
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length()); // approx sizing
            boolean wasChanged = false;
            for (byte b : bytes) {
                if (PRESERVE.matches(toChar(b))) {
                    bos.write(b);
                } else if (b == ' ') {
                    bos.write('+');
                } else {
                    bos.write('%');
                    char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                    char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                    bos.write(hex1);
                    bos.write(hex2);
                    wasChanged = true;
                }
            }
            return wasChanged ? new String(bos.toByteArray(), StandardCharsets.UTF_8) : source;
        }

        // converts the given (signed) byte into an (unsigned) char
        private static char toChar(byte theByte) {
            if (theByte < 0) {
                return (char) (256 + theByte);
            } else {
                return (char) theByte;
            }
        }
    }
}
