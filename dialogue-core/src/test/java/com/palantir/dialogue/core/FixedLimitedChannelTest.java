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
package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FixedLimitedChannelTest {

    private final DialogueClientMetrics metrics = DialogueClientMetrics.of(new DefaultTaggedMetricRegistry());

    @Test
    public void testExhaustion() {
        SettableFuture<Response> result = SettableFuture.create();
        LimitedChannel delegate = Mockito.mock(LimitedChannel.class);
        when(delegate.maybeExecute(any())).thenReturn(Optional.of(result));
        LimitedChannel channel = new FixedLimitedChannel(delegate, 1, metrics);
        // consume the single permit
        assertThat(channel.maybeExecute(Mockito.mock(LimitedRequest.class))).isPresent();
        // no permits available
        assertThat(channel.maybeExecute(Mockito.mock(LimitedRequest.class))).isEmpty();
        // after completing the future more requests can be sent
        result.cancel(false);
        assertThat(channel.maybeExecute(Mockito.mock(LimitedRequest.class))).isPresent();
    }

    @Test
    public void testPermitReturnedOnException() {
        LimitedChannel delegate = Mockito.mock(LimitedChannel.class);
        when(delegate.maybeExecute(any())).thenThrow(new RuntimeException("expected"));
        LimitedChannel channel = new FixedLimitedChannel(delegate, 1, metrics);
        // Exceptions shouldn't be thrown, but shouldn't produce leaks either.
        assertThatThrownBy(() -> channel.maybeExecute(Mockito.mock(LimitedRequest.class)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("expected");
        // If the second call results in am empty value, the first call failed to return a permit.
        assertThatThrownBy(() -> channel.maybeExecute(Mockito.mock(LimitedRequest.class)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("expected");
    }
}
