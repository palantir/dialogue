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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
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
    private final DefaultConjureRuntime runtime =
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
        SampleServiceBlocking.of((Channel) channel, runtime);

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void plain_codegen_uses_the_EndpointChannelFactory_factory() {
        SampleServiceBlocking.of((EndpointChannelFactory) channel, runtime);

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void live_reloading_wrapper_still_uses_the_EndpointChannelFactory_channel() {
        LiveReloadingChannel live = new LiveReloadingChannel(Refreshable.create(channel), runtime.clients());
        assertThat(SampleServiceAsync.of((Channel) live, runtime).getMyAlias()).isNotDone();

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }

    @Test
    void live_reloading_wrapper_still_uses_the_EndpointChannelFactory_factory() {
        LiveReloadingChannel live = new LiveReloadingChannel(Refreshable.only(channel), runtime.clients());
        assertThat(SampleServiceAsync.of((EndpointChannelFactory) live, runtime).getMyAlias())
                .isNotDone();

        // ensure we use the bind method
        verify(channel, atLeastOnce()).endpoint(any());
    }
}
