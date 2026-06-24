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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.DialogueService;
import com.palantir.dialogue.DialogueServiceFactory;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.clients.ReloadingClientFactory.LiveReloadingChannel;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReloadingClientFactoryTest {
    private final DefaultConjureRuntime defaultRuntime =
            DefaultConjureRuntime.builder().build();

    interface Foo extends Channel, EndpointChannelFactory {}

    @SuppressWarnings("for-rollout:deprecation")
    @Mock(lenient = true)
    Foo channel;

    @SuppressWarnings("for-rollout:deprecation")
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

    @Test
    void withMaxResponseSize_deserializers_respects_max_size() {
        ReloadingFactory factory = getFactory().withMaxResponseSize(128L);

        ConjureRuntime runtime = getRuntime(factory);

        assertThat(deserializeString(runtime, "\"test\"")).isEqualTo("test");

        assertThatException()
                .isThrownBy(() -> deserializeString(runtime, "\"" + "test".repeat(1280) + "\""))
                .isInstanceOf(ResponseSizeTooLargeException.class);
    }

    @Test
    void withMaxResponseSize_deserializers_respects_max_size_for_errors() {
        ReloadingFactory factory = getFactory().withMaxResponseSize(128L);

        ConjureRuntime runtime = getRuntime(factory);

        assertThatException()
                .isThrownBy(() -> deserializeError(runtime, "{\"errorCode\":\"errorCode\",\"errorName\":\"test\"}"))
                .isInstanceOf(RemoteException.class)
                .extracting(e -> ((RemoteException) e).getError())
                .isEqualTo(SerializableError.builder()
                        .errorCode("errorCode")
                        .errorName("test")
                        .build());

        assertThatException()
                .isThrownBy(() -> deserializeError(
                        runtime, "{\"errorCode\":\"errorCode\",\"errorName\":\"" + "test".repeat(1280) + "\"}"))
                .isInstanceOf(ResponseSizeTooLargeException.class);
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

    private static String deserializeString(ConjureRuntime runtime, String body) {
        return deserialize(runtime, 200, body, new TypeMarker<>() {});
    }

    private static void deserializeError(ConjureRuntime runtime, String body) {
        deserialize(runtime, 400, body, new TypeMarker<>() {});
    }

    private static <T> T deserialize(ConjureRuntime runtime, int code, String body, TypeMarker<T> type) {
        TestResponse response = TestResponse.withBody(body).withHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        response.code(code);
        return runtime.bodySerDe().deserializer(type).deserialize(response);
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
