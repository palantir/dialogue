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

import com.google.common.collect.ImmutableList;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.CloseRecordingInputStream;
import com.palantir.dialogue.TestResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class BinaryEncodingTest {

    @Test
    public void testBinary() throws IOException {
        TestResponse response = new TestResponse().code(200).contentType("application/octet-stream");
        BodySerDe serializers = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(new ConjureBodySerDeTest.StubEncoding("application/json"))),
                ConjureErrorDecoder.INSTANCE,
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
        InputStream deserialized = serializers.inputStreamDeserializer().deserialize(response);
        assertThat(deserialized.available()).isEqualTo(0);
        CloseRecordingInputStream rawInputStream = response.body();
        rawInputStream.assertNotClosed();
        assertThat(response.isClosed())
                .describedAs("response is unclosed initially")
                .isFalse();

        deserialized.close();
        assertThat(response.isClosed())
                .describedAs(
                        "Response#close was never called, but no big deal because the body is the only resource worth"
                                + " closing")
                .isFalse();
    }

    @Test
    public void testBinary_optional_present() throws IOException {
        TestResponse response = new TestResponse().code(200).contentType("application/octet-stream");
        BodySerDe serializers = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(new ConjureBodySerDeTest.StubEncoding("application/json"))),
                ConjureErrorDecoder.INSTANCE,
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
        Optional<InputStream> maybe =
                serializers.optionalInputStreamDeserializer().deserialize(response);
        assertThat(maybe).isPresent();
        InputStream deserialized = maybe.get();
        assertThat(deserialized.available()).isEqualTo(0);
        CloseRecordingInputStream rawInputStream = response.body();
        rawInputStream.assertNotClosed();
        assertThat(response.isClosed())
                .describedAs("response is unclosed initially")
                .isFalse();

        deserialized.close();
        assertThat(response.isClosed())
                .describedAs(
                        "Response#close was never called, but no big deal because the body is the only resource worth"
                                + " closing")
                .isFalse();
    }
}
