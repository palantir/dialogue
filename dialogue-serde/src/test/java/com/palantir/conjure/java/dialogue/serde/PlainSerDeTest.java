/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.BearerToken;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import org.junit.Test;

public final class PlainSerDeTest {

    private static final PlainSerDe PLAIN = ConjurePlainSerDe.INSTANCE;

    @Test
    public void testDeserializeBearerToken() throws Exception {
        runDeserializerTest("BearerToken", "token", BearerToken.valueOf("token"));
    }

    @Test
    public void testDeserializeBoolean() throws Exception {
        runDeserializerTest("Boolean", "true", true);
    }

    @Test
    public void testDeserializeDateTime() throws Exception {
        runDeserializerTest("DateTime", "2018-07-19T08:11:21+00:00", OffsetDateTime.parse("2018-07-19T08:11:21+00:00"));
    }

    @Test
    public void testDeserializeDouble() throws Exception {
        runDeserializerTest("Double", "1.234", 1.234, OptionalDouble::of);
    }

    @Test
    public void testDeserializeInteger() throws Exception {
        runDeserializerTest("Integer", "13", 13, OptionalInt::of);
    }

    @Test
    public void testDeserializeRid() throws Exception {
        runDeserializerTest("Rid", "ri.service.instance.folder.foo",
                ResourceIdentifier.of("ri.service.instance.folder.foo"));
    }

    @Test
    public void testDeserializeSafeLong() throws Exception {
        runDeserializerTest("SafeLong", "9007199254740990", SafeLong.of(9007199254740990L));
    }

    @Test
    public void testDeserializeString() throws Exception {
        runDeserializerTest("String", "hello, world!", "hello, world!");
    }

    @Test
    public void testDeserializeUuid() throws Exception {
        runDeserializerTest("Uuid", "90a8481a-2ef5-4c64-83fc-04a9b369e2b8",
                UUID.fromString("90a8481a-2ef5-4c64-83fc-04a9b369e2b8"));
    }

    @Test
    public void testBearerTokensNotIncludedInThrowable() {
        assertThatLoggableExceptionThrownBy(() ->
                PLAIN.deserializeBearerToken(ImmutableList.of("one", "two", "three")))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasLogMessage("Expected one element")
                .hasExactlyArgs(SafeArg.of("size", 3));
    }

    @Test
    public void testValuesAreLoggedUnsafe() {
        assertThatLoggableExceptionThrownBy(() ->
                PLAIN.deserializeString(ImmutableList.of("one", "two", "three")))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasLogMessage("Expected one element")
                .hasExactlyArgs(SafeArg.of("size", 3),
                        UnsafeArg.of("received", ImmutableList.of("one", "two", "three")));
    }

    private static <T> void runDeserializerTest(String typeName, String plainIn, T want) throws Exception {
        runDeserializerTest(typeName, plainIn, want, Optional::of);
    }

    private static <T> void runDeserializerTest(String typeName, String plainIn, T want,
            Function<T, Object> createOptional) throws Exception {
        assertThat(PlainSerDe.class.getMethod("deserialize" + typeName, String.class)
                .invoke(PLAIN, plainIn)).isEqualTo(want);

        assertThatLoggableExceptionThrownBy(() -> {
            try {
                PlainSerDe.class.getMethod("deserialize" + typeName, String.class)
                        .invoke(PLAIN, new Object[] {null});
            } catch (InvocationTargetException ite) {
                throw ite.getCause();
            }
        })
                .describedAs("invoking a string emptyBodyDeserializer with null should result in an IAE")
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasLogMessage("Value is required");

        assertThat(PlainSerDe.class.getMethod("deserialize" + typeName, Iterable.class)
                .invoke(PLAIN, ImmutableList.of(plainIn))).isEqualTo(want);

        Method optionalStringDeserializer = PlainSerDe.class.getMethod("deserializeOptional" + typeName, String.class);
        assertThat(optionalStringDeserializer.invoke(PLAIN, plainIn)).isEqualTo(createOptional.apply(want));

        assertThat(optionalStringDeserializer.invoke(PLAIN, new Object[] { null }))
                .isEqualTo(createEmptyOptional(optionalStringDeserializer));

        assertThat(PlainSerDe.class.getMethod("deserializeOptional" + typeName, Iterable.class)
                .invoke(PLAIN, ImmutableList.of(plainIn))).isEqualTo(createOptional.apply(want));
    }

    private static Object createEmptyOptional(Method deserializerMethod) throws ReflectiveOperationException {
        return deserializerMethod.getReturnType().getDeclaredMethod("empty").invoke(null);
    }

}
