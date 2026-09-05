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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.errors.ConjureErrorParameterFormat;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.DialogueService;
import com.palantir.dialogue.DialogueServiceFactory;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.clients.ReloadingClientFactory.LiveReloadingChannel;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReloadingClientFactoryTest {
    private final DefaultConjureRuntime defaultRuntime =
            DefaultConjureRuntime.builder().build();

    interface Foo extends Channel, EndpointChannelFactory {}

    @Mock(lenient = true)
    Foo channel;

    @Mock(lenient = true)
    EndpointChannel endpointChannel;

    @BeforeEach
    void beforeEach() {
        when(endpointChannel.execute(any())).thenReturn(SettableFuture.create());
        when(channel.execute(any(), any())).thenReturn(SettableFuture.create());
        when(channel.endpoint(any())).thenReturn(endpointChannel);
    }

    @Test
    void plain_codegen_uses_the_EndpointChannelFactory_channel() {
        SampleServiceBlocking.of((Channel) channel, defaultRuntime);

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void plain_codegen_uses_the_EndpointChannelFactory_factory() {
        SampleServiceBlocking.of((EndpointChannelFactory) channel, defaultRuntime);

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void live_reloading_wrapper_still_uses_the_EndpointChannelFactory_channel() {
        LiveReloadingChannel live = new LiveReloadingChannel(Refreshable.create(channel), defaultRuntime.clients());
        assertThat(SampleServiceAsync.of((Channel) live, defaultRuntime).getMyAlias())
                .isNotDone();

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void live_reloading_wrapper_still_uses_the_EndpointChannelFactory_factory() {
        LiveReloadingChannel live = new LiveReloadingChannel(Refreshable.only(channel), defaultRuntime.clients());
        assertThat(SampleServiceAsync.of((EndpointChannelFactory) live, defaultRuntime)
                        .getMyAlias())
                .isNotDone();

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void withConjureErrorParameterSerializationFormat_wraps_runtime_with_specified_format() {
        ReloadingClientFactory factory = getFactory();

        // Get the original runtime
        ConjureRuntime originalRuntime = getRuntime(factory);
        assertThat(originalRuntime.bodySerDe().errorParameterFormat()).isEmpty();

        // Set the error parameter format to JSON
        ReloadingFactory modifiedFactory =
                factory.withConjureErrorParameterFormat(ConjureErrorParameterFormat.JSON_FORMAT);

        ConjureRuntime modifiedRuntime = getRuntime(modifiedFactory);

        assertThat(modifiedRuntime.bodySerDe().errorParameterFormat())
                .contains(ConjureErrorParameterFormat.JSON_FORMAT);

        // Verify that the original runtime is unchanged
        assertThat(getRuntime(factory).bodySerDe().errorParameterFormat()).isEmpty();
    }

    @Test
    void withRuntime_after_withConjureErrorParameterFormat_preserves_format() {
        ReloadingFactory modifiedFactory = getFactory()
                .withConjureErrorParameterFormat(ConjureErrorParameterFormat.JSON_FORMAT)
                .withRuntime(DefaultConjureRuntime.builder().build());

        RuntimeCapturingService service = modifiedFactory.get(RuntimeCapturingService.class, "foo");

        assertThat(service.runtime().bodySerDe().errorParameterFormat())
                .contains(ConjureErrorParameterFormat.JSON_FORMAT);
    }

    private static <T> ExceptionDeserializerArgs<T> exceptionDeserializerArgs(TypeMarker<T> type) {
        return ExceptionDeserializerArgs.<T>builder().returnType(type).build();
    }

    enum Deserializers {
        BASIC,
        WITH_EXCEPTION_ARGS,
        EMPTY,
        EMPTY_WITH_EXCEPTION_ARGS,
        INPUT_STREAM,
        INPUT_STREAM_WITH_EXCEPTION_ARGS,
        OPTIONAL_INPUT_STREAM,
        OPTIONAL_INPUT_STREAM_WITH_EXCEPTION_ARGS;

        private static <T> Deserializer<T> unwrap(Deserializer<Optional<T>> deserializer) {
            return new Deserializer<>() {
                @Override
                public T deserialize(Response response) {
                    return deserializer.deserialize(response).get();
                }

                @Override
                public Optional<String> accepts() {
                    return deserializer.accepts();
                }
            };
        }

        public Deserializer<?> deserializer(ConjureRuntime runtime) {
            BodySerDe bodySerDe = runtime.bodySerDe();
            return switch (this) {
                case BASIC -> bodySerDe.deserializer(new TypeMarker<String>() {});
                case WITH_EXCEPTION_ARGS ->
                    bodySerDe.deserializer(exceptionDeserializerArgs(new TypeMarker<String>() {}));
                case EMPTY -> bodySerDe.emptyBodyDeserializer();
                case EMPTY_WITH_EXCEPTION_ARGS ->
                    bodySerDe.emptyBodyDeserializer(exceptionDeserializerArgs(new TypeMarker<>() {}));
                case INPUT_STREAM -> bodySerDe.inputStreamDeserializer();
                case INPUT_STREAM_WITH_EXCEPTION_ARGS ->
                    bodySerDe.inputStreamDeserializer(exceptionDeserializerArgs(new TypeMarker<>() {}));
                case OPTIONAL_INPUT_STREAM -> unwrap(bodySerDe.optionalInputStreamDeserializer());
                case OPTIONAL_INPUT_STREAM_WITH_EXCEPTION_ARGS ->
                    unwrap(bodySerDe.optionalInputStreamDeserializer(exceptionDeserializerArgs(new TypeMarker<>() {})));
            };
        }
    }

    private static Stream<Deserializers> alwaysLimitingDeserializers() {
        return Stream.of(Deserializers.BASIC, Deserializers.WITH_EXCEPTION_ARGS);
    }

    private static Stream<Deserializers> errorLimitingDeserializers() {
        return Arrays.stream(Deserializers.values());
    }

    private static Stream<Deserializers> inputStreamDeserializers() {
        return Stream.of(
                Deserializers.INPUT_STREAM,
                Deserializers.INPUT_STREAM_WITH_EXCEPTION_ARGS,
                Deserializers.OPTIONAL_INPUT_STREAM,
                Deserializers.OPTIONAL_INPUT_STREAM_WITH_EXCEPTION_ARGS);
    }

    @ParameterizedTest
    @MethodSource("alwaysLimitingDeserializers")
    void withMaxResponseSize_deserializers_respects_max_size(Deserializers arg) {
        ReloadingFactory factory = getFactory().withMaxResponseSize(128L);

        ConjureRuntime runtime = getRuntime(factory);

        Deserializer<String> deserializer = (Deserializer<String>) arg.deserializer(runtime);
        assertThat(deserialize(deserializer, "\"test\"")).isEqualTo("test");

        assertThatException()
                .isThrownBy(() -> deserialize(deserializer, "\"" + "test".repeat(1280) + "\""))
                .isInstanceOf(ResponseSizeTooLargeException.class);
    }

    @ParameterizedTest
    @MethodSource("errorLimitingDeserializers")
    void withMaxResponseSize_deserializers_respects_max_size_for_errors(Deserializers arg) {
        ReloadingFactory factory = getFactory().withMaxResponseSize(128L);

        ConjureRuntime runtime = getRuntime(factory);

        Deserializer<?> deserializer = arg.deserializer(runtime);
        assertThatException()
                .isThrownBy(
                        () -> deserializeError(deserializer, "{\"errorCode\":\"errorCode\",\"errorName\":\"test\"}"))
                .isInstanceOf(RemoteException.class)
                .extracting(e -> ((RemoteException) e).getError())
                .isEqualTo(SerializableError.builder()
                        .errorCode("errorCode")
                        .errorName("test")
                        .build());

        assertThatException()
                .isThrownBy(() -> deserializeError(
                        deserializer, "{\"errorCode\":\"errorCode\",\"errorName\":\"" + "test".repeat(1280) + "\"}"))
                .isInstanceOf(ResponseSizeTooLargeException.class);
    }

    @ParameterizedTest
    @MethodSource("inputStreamDeserializers")
    void withMaxResponseSize_deserializers_ignores_max_size_for_input_streams(Deserializers arg) throws IOException {
        ReloadingFactory factory = getFactory().withMaxResponseSize(128L);

        ConjureRuntime runtime = getRuntime(factory);

        Deserializer<InputStream> deserializer = (Deserializer<InputStream>) arg.deserializer(runtime);
        String shortString = "test";
        try (InputStream deserialized = deserialize(deserializer, "application/octet-stream", 200, shortString)) {
            assertThat(new String(deserialized.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(shortString);
        }

        String longString = "test".repeat(1280);
        try (InputStream deserialized = deserialize(deserializer, "application/octet-stream", 200, longString)) {
            assertThat(new String(deserialized.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(longString);
        }
    }

    private static ReloadingClientFactory getFactory() {
        ChannelCache mockCache = mock(ChannelCache.class);
        ImmutableReloadingParams params = ImmutableReloadingParams.builder()
                .scb(Refreshable.only(ServicesConfigBlock.builder().build()))
                .build();
        return new ReloadingClientFactory(params, mockCache);
    }

    private static ConjureRuntime getRuntime(ReloadingFactory factory) {
        RuntimeCapturingService service = factory.get(RuntimeCapturingService.class, "foo");
        return service.runtime();
    }

    private static void deserializeError(Deserializer<?> deserializer, String body) {
        deserialize(deserializer, "application/json", 400, body);
        fail("Should have thrown an exception upon deserializing");
    }

    private static <T> T deserialize(Deserializer<T> deserializer, String body) {
        return deserialize(deserializer, "application/json", 200, body);
    }

    private static <T> T deserialize(Deserializer<T> deserializer, String contentType, int code, String body) {
        TestResponse response = TestResponse.withBody(body).withHeader(HttpHeaders.CONTENT_TYPE, contentType);
        response.code(code);
        return deserializer.deserialize(response);
    }

    @DialogueService(RuntimeCapturingService.Factory.class)
    private record RuntimeCapturingService(ConjureRuntime runtime) {
        static RuntimeCapturingService of(ConjureRuntime runtime) {
            return new RuntimeCapturingService(runtime);
        }

        public static final class Factory implements DialogueServiceFactory<RuntimeCapturingService> {
            @Override
            public RuntimeCapturingService create(
                    EndpointChannelFactory _endpointChannelFactory, ConjureRuntime conjureRuntime) {
                return RuntimeCapturingService.of(conjureRuntime);
            }
        }
    }
}
