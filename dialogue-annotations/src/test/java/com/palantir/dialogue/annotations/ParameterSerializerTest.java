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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public final class ParameterSerializerTest {

    private static final ParameterSerializer PLAIN = DefaultParameterSerializer.INSTANCE;

    @Test
    public void testSerializeBearerToken() {
        BearerToken in = BearerToken.valueOf("token");
        String out = "token";
        assertThat(PLAIN.serializeBearerToken(in)).isEqualTo(out);
    }

    @Test
    public void testSerializeAuthHeader() {
        AuthHeader in = AuthHeader.of(BearerToken.valueOf("token"));
        String out = "Bearer token";
        assertThat(PLAIN.serializeAuthHeader(in)).isEqualTo(out);
    }

    @Test
    public void testSerializeBoolean() {
        for (Pair<Boolean, String> inOut : Arrays.asList(Pair.of(true, "true"), Pair.of(false, "false"))) {
            boolean in = inOut.getLeft();
            String out = inOut.getRight();
            assertThat(PLAIN.serializeBoolean(in)).isEqualTo(out);
        }
    }

    @Test
    public void testSerializeDateTime() {
        OffsetDateTime in = OffsetDateTime.parse("2018-07-19T08:11:21+00:00");
        String out = "2018-07-19T08:11:21Z";
        assertThat(PLAIN.serializeDateTime(in)).isEqualTo(out);
    }

    @Test
    public void testSerializeDouble() {
        for (Pair<Double, String> inOut : Arrays.asList(Pair.of(1.234, "1.234"), Pair.of(1.2340, "1.234"))) {
            double in = inOut.getLeft();
            String out = inOut.getRight();
            assertThat(PLAIN.serializeDouble(in)).isEqualTo(out);
        }
    }

    @Test
    public void testSerializeFloat() {
        for (Pair<Float, String> inOut : Arrays.asList(Pair.of(1.234f, "1.234"), Pair.of(1.2340f, "1.234"))) {
            float in = inOut.getLeft();
            String out = inOut.getRight();
            assertThat(PLAIN.serializeFloat(in)).isEqualTo(out);
        }
    }

    @Test
    public void testSerializeInteger() {
        for (Pair<Integer, String> inOut : Arrays.asList(Pair.of(42, "42"), Pair.of(-42, "-42"))) {
            int in = inOut.getLeft();
            String out = inOut.getRight();
            assertThat(PLAIN.serializeInteger(in)).isEqualTo(out);
        }
    }

    @Test
    public void testSerializeLong() {
        for (Pair<Long, String> inOut : Arrays.asList(Pair.of(42L, "42"), Pair.of(-42L, "-42"))) {
            long in = inOut.getLeft();
            String out = inOut.getRight();
            assertThat(PLAIN.serializeLong(in)).isEqualTo(out);
        }
    }

    @Test
    public void testChar() {
        for (Pair<Character, String> inOut : Arrays.asList(Pair.of('c', "c"), Pair.of('\n', "\n"))) {
            char in = inOut.getLeft();
            String out = inOut.getRight();
            assertThat(PLAIN.serializeChar(in)).isEqualTo(out);
        }
    }

    @Test
    public void testSerializeRid() {
        ResourceIdentifier in = ResourceIdentifier.of("ri.service.instance.folder.foo");
        String out = "ri.service.instance.folder.foo";
        assertThat(PLAIN.serializeRid(in)).isEqualTo(out);
    }

    @Test
    public void testSerializeSafeLong() {
        SafeLong in = SafeLong.of(9007199254740990L);
        String out = "9007199254740990";
        assertThat(PLAIN.serializeSafeLong(in)).isEqualTo(out);
    }

    @Test
    public void testSerializeString() {
        String in = "hello world!";
        String out = "hello world!";
        assertThat(PLAIN.serializeString(in)).isEqualTo(out);
    }

    @Test
    public void testSerializeUuid() {
        UUID in = UUID.fromString("90a8481a-2ef5-4c64-83fc-04a9b369e2b8");
        String out = "90a8481a-2ef5-4c64-83fc-04a9b369e2b8";
        assertThat(PLAIN.serializeUuid(in)).isEqualTo(out);
    }
}
