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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConjureBodySerDeTest {

    private static final TypeMarker<String> TYPE = new TypeMarker<String>() {};
    private static final TypeMarker<Optional<String>> OPTIONAL_TYPE = new TypeMarker<Optional<String>>() {};

    private ErrorDecoder errorDecoder = ErrorDecoder.INSTANCE;

    @Test
    public void testRequestContentType() throws IOException {

        TestResponse response = new TestResponse().contentType("text/plain");
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        String value = serializers.deserializer(TYPE).deserialize(response);
        assertThat(value).isEqualTo("text/plain");
    }

    @Test
    public void testRequestOptionalEmpty() {
        TestResponse response = new TestResponse().code(204);
        BodySerDe serializers = conjureBodySerDe("application/json");
        Optional<String> value = serializers.deserializer(OPTIONAL_TYPE).deserialize(response);
        assertThat(value).isEmpty();
    }

    private ConjureBodySerDe conjureBodySerDe(String... contentTypes) {
        return new ConjureBodySerDe(
                Arrays.stream(contentTypes)
                        .map(c -> WeightedEncoding.of(new StubEncoding(c)))
                        .collect(ImmutableList.toImmutableList()),
                errorDecoder,
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
    }

    @Test
    public void testRequestNoContentType() {
        TestResponse response = new TestResponse();
        BodySerDe serializers = conjureBodySerDe("application/json");
        assertThatThrownBy(() -> serializers.deserializer(TYPE).deserialize(response))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessageContaining("Response is missing Content-Type header");
    }

    @Test
    public void testUnsupportedRequestContentType() {
        TestResponse response = new TestResponse().contentType("application/unknown");
        BodySerDe serializers = conjureBodySerDe("application/json");
        assertThatThrownBy(() -> serializers.deserializer(TYPE).deserialize(response))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessageContaining("Unsupported Content-Type");
    }

    @Test
    public void testDefaultContentType() throws IOException {
        BodySerDe serializers = conjureBodySerDe("text/plain", "application/json");
        // first encoding is default
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo("text/plain");
        assertThat(serializers.deserializer(TYPE).accepts()).hasValue("text/plain, application/json");
    }

    @Test
    public void testAcceptBasedOnWeight() throws IOException {
        Encoding json = new StubEncoding("application/json");
        Encoding plain = new StubEncoding("text/plain");

        BodySerDe serializers = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(plain, .5), WeightedEncoding.of(json, 1)),
                ErrorDecoder.INSTANCE,
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
        // first encoding is default
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo(plain.getContentType());
        assertThat(serializers.deserializer(TYPE).accepts()).hasValue("application/json, text/plain");
    }

    @Test
    public void testResponseNoContentType() throws IOException {
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo("application/json");
    }

    @Test
    public void testRequestUnknownContentType() throws IOException {
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        RequestBody body = serializers.serializer(TYPE).serialize("test");
        assertThat(body.contentType()).isEqualTo("application/json");
    }

    @Test
    public void testErrorsDecoded() {
        TestResponse response = new TestResponse().code(400);

        ServiceException serviceException = new ServiceException(ErrorType.INVALID_ARGUMENT);
        SerializableError serialized = SerializableError.forException(serviceException);
        errorDecoder = mock(ErrorDecoder.class);
        when(errorDecoder.isError(response)).thenReturn(true);
        when(errorDecoder.decode(response)).thenReturn(new RemoteException(serialized, 400));

        BodySerDe serializers = conjureBodySerDe("text/plain");

        assertThatExceptionOfType(RemoteException.class)
                .isThrownBy(() -> serializers.deserializer(TYPE).deserialize(response));

        assertThat(response.isClosed()).describedAs("response should be closed").isTrue();
        assertThat(response.body().isClosed())
                .describedAs("inputstream should be closed")
                .isTrue();
    }

    @Test
    public void testBinary_optional_empty() {
        TestResponse response = new TestResponse().code(204);
        BodySerDe serializers = conjureBodySerDe("application/json");
        assertThat(serializers.optionalInputStreamDeserializer().deserialize(response))
                .isEmpty();
        assertThat(response.body().isClosed())
                .describedAs("inputstream should be closed")
                .isTrue();
        assertThat(response.isClosed()).describedAs("response should be closed").isTrue();
    }

    @Test
    public void if_deserialize_throws_response_is_still_closed() {
        TestResponse response = new TestResponse().code(200).contentType("application/json");
        BodySerDe serializers = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(BrokenEncoding.INSTANCE)),
                ErrorDecoder.INSTANCE,
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
        assertThatThrownBy(() -> serializers.deserializer(TYPE).deserialize(response))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessage("brokenEncoding is broken");
        assertThat(response.body().isClosed())
                .describedAs("inputstream should be closed")
                .isTrue();
        assertThat(response.isClosed()).describedAs("response should be closed").isTrue();
    }

    enum BrokenEncoding implements Encoding {
        INSTANCE;

        @Override
        public <T> Encoding.Serializer<T> serializer(TypeMarker<T> _type) {
            throw new UnsupportedOperationException("unimplemented");
        }

        @Override
        public <T> Encoding.Deserializer<T> deserializer(TypeMarker<T> _type) {
            return _input -> {
                throw new SafeRuntimeException("brokenEncoding is broken");
            };
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public boolean supportsContentType(String _contentType) {
            return true;
        }
    }

    @Test
    public void testEmptyResponse_success() {
        TestResponse response = new TestResponse().code(204);
        BodySerDe serializers = conjureBodySerDe("application/json");
        serializers.emptyBodyDeserializer().deserialize(response);
    }

    @Test
    public void testEmptyResponse_failure() {
        TestResponse response = new TestResponse().code(400);

        ServiceException serviceException = new ServiceException(ErrorType.INVALID_ARGUMENT);
        SerializableError serialized = SerializableError.forException(serviceException);
        errorDecoder = mock(ErrorDecoder.class);
        when(errorDecoder.isError(response)).thenReturn(true);
        when(errorDecoder.decode(response)).thenReturn(new RemoteException(serialized, 400));

        BodySerDe serializers = conjureBodySerDe("application/json");

        assertThatExceptionOfType(RemoteException.class)
                .isThrownBy(() -> serializers.emptyBodyDeserializer().deserialize(response));
    }

    @Test
    public void testEmptyResponse_list() {
        BodySerDe serde = DefaultConjureRuntime.builder().build().bodySerDe();
        List<String> result =
                serde.deserializer(new TypeMarker<List<String>>() {}).deserialize(new TestResponse().code(204));
        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testEmptyResponse_list_raw() {
        BodySerDe serde = DefaultConjureRuntime.builder().build().bodySerDe();
        List result = serde.deserializer(new TypeMarker<List>() {}).deserialize(new TestResponse().code(204));
        assertThat(result).isEmpty();
    }

    @Test
    public void testRepeatableBinaryRequestBodyProducesRepeatableRequest() {
        BodySerDe serde = DefaultConjureRuntime.builder().build().bodySerDe();
        RequestBody requestBody = serde.serialize(new BinaryRequestBody() {
            @Override
            public void write(OutputStream _requestBody) {}

            @Override
            public boolean repeatable() {
                return true;
            }
        });
        assertThat(requestBody.repeatable()).isTrue();
    }

    @Test
    public void testNonRepeatableBinaryRequestBodyProducesNonRepeatableRequest() {
        BodySerDe serde = DefaultConjureRuntime.builder().build().bodySerDe();
        RequestBody requestBody = serde.serialize(new BinaryRequestBody() {
            @Override
            public void write(OutputStream _requestBody) {}

            @Override
            public boolean repeatable() {
                return false;
            }
        });
        assertThat(requestBody.repeatable()).isFalse();
    }

    @Test
    public void testDefaultBinaryRequestBodyProducesNonRepeatableRequestBody() {
        BodySerDe serde = DefaultConjureRuntime.builder().build().bodySerDe();
        RequestBody requestBody = serde.serialize(_requestBody -> {});
        assertThat(requestBody.repeatable()).isFalse();
    }

    /** Deserializes requests as the configured content type. */
    public static final class StubEncoding implements Encoding {

        private final String contentType;

        StubEncoding(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public <T> Encoding.Serializer<T> serializer(TypeMarker<T> _type) {
            return (_value, _output) -> {
                // nop
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Encoding.Deserializer<T> deserializer(TypeMarker<T> type) {
            return _input -> {
                Preconditions.checkArgument(TYPE.equals(type), "This stub encoding only supports String");
                return (T) getContentType();
            };
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean supportsContentType(String input) {
            return contentType.equals(input);
        }

        @Override
        public String toString() {
            return "StubEncoding{" + contentType + '}';
        }
    }
}
