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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorSerializableError;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TypeMarker;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public final class ExceptionDeserializerArgsTest {

    @Test
    public void testEqualInstances() {
        ExceptionDeserializerArgs<String> args1 = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        ExceptionDeserializerArgs<String> args2 = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        assertThat(args1).isEqualTo(args2);
        assertThat(args1.hashCode()).isEqualTo(args2.hashCode());
    }

    @Test
    public void testDifferentReturnType() {
        ExceptionDeserializerArgs<String> stringArgs = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        ExceptionDeserializerArgs<InputStream> inputStreamArgs =
                ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs();
        assertThat(inputStreamArgs).isNotEqualTo(stringArgs);
        assertThat(inputStreamArgs.hashCode()).isNotEqualTo(stringArgs.hashCode());
    }

    @Test
    public void testDifferentErrorNames() {
        ExceptionDeserializerArgs<String> args1 = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        ExceptionDeserializerArgs<String> args2 = ExceptionDeserializerArgs.<String>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        "differentErrorName",
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();

        assertThat(args1).isNotEqualTo(args2);
        assertThat(args1.hashCode()).isNotEqualTo(args2.hashCode());
    }

    @Test
    public void testDifferentExceptionEntries() {
        ExceptionDeserializerArgs<String> args1 = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        ExceptionDeserializerArgs<String> args2 = ExceptionDeserializerArgs.<String>builder()
                .returnType(new TypeMarker<>() {})
                // This is also in args1
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                // This is not in args1
                .exception(
                        "differentErrorName",
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();

        assertThat(args1).isNotEqualTo(args2);
        assertThat(args1.hashCode()).isNotEqualTo(args2.hashCode());
    }

    @Test
    public void testToString() {
        ExceptionDeserializerArgs<String> args = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        String toString = args.toString();
        assertThat(toString)
                .isEqualTo("ExceptionDeserializerArgs{returnType=TypeMarker{type=class java.lang.String}, "
                        + "errorNameToExceptionTypeMarkers={Conjure:TestError="
                        + "ErrorExceptionPair[errorType=TypeMarker{type=class"
                        + " com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils"
                        + "$TestErrorSerializableError},"
                        + " exceptionType=TypeMarker{type=class"
                        + " com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils"
                        + "$TestErrorException}"
                        + "]}}");
    }
}
