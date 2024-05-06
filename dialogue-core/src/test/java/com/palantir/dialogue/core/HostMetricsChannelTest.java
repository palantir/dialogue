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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.testing.Assertions;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HostMetricsChannelTest {

    @Mock
    Channel mockChannel;

    @Mock
    DialogueChannelFactory factory;

    @Mock
    Ticker ticker;

    @Test
    void shortcircuit_if_there_is_no_sink() {
        Channel channel = HostMetricsChannel.create(
                config(TestConfigurations.create("https://unused", "https://unused2")),
                mockChannel,
                "https://foo:1001");

        assertThat(channel).isSameAs(mockChannel);
    }

    @Test
    void shortcircuit_if_someone_passes_in_the_noop_enum() {
        Channel channel = HostMetricsChannel.create(
                config(ClientConfiguration.builder()
                        .from(TestConfigurations.create("https://unused", "https://unused2"))
                        .hostEventsSink(NoOpHostEventsSink.INSTANCE)
                        .build()),
                mockChannel,
                "https://foo:1001");

        assertThat(channel).isSameAs(mockChannel);
    }

    public enum NoOpHostEventsSink implements HostEventsSink {
        INSTANCE;

        @Override
        public void record(String _serviceName, String _hostname, int _port, int _statusCode, long _micros) {
            // do nothing
        }

        @Override
        public void recordIoException(String _serviceName, String _hostname, int _port) {
            // do nothing
        }
    }

    @Test
    void calls_sink_when_response_comes_back() {
        AtomicBoolean recorded = new AtomicBoolean();
        Channel channel = HostMetricsChannel.create(
                config(ClientConfiguration.builder()
                        .from(TestConfigurations.create("https://unused", "https://unused2"))
                        .hostEventsSink(new HostEventsSink() {
                            @Override
                            public void record(
                                    String serviceName, String hostname, int port, int statusCode, long micros) {
                                assertThat(serviceName).isEqualTo("channelName");
                                assertThat(hostname).isEqualTo("foo");
                                assertThat(port).isEqualTo(1001);
                                assertThat(statusCode).isEqualTo(200);
                                assertThat(micros).isEqualTo(TimeUnit.SECONDS.toMicros(3));
                                recorded.set(true);
                            }

                            @Override
                            public void recordIoException(String _serviceName, String _hostname, int _port) {
                                Assertions.fail("no IOExceptions expected");
                            }
                        })
                        .build()),
                mockChannel,
                "https://foo:1001");

        SettableFuture<Response> settable = SettableFuture.create();
        when(mockChannel.execute(any(), any())).thenReturn(settable);

        ListenableFuture<Response> future =
                channel.execute(TestEndpoint.GET, Request.builder().build());
        when(ticker.read()).thenReturn(Duration.ofSeconds(3).toNanos());

        settable.set(new TestResponse().code(200));
        assertThat(recorded).isTrue();
        assertThat(future).isDone();
    }

    private ImmutableConfig config(ClientConfiguration rawConfig) {
        return ImmutableConfig.builder()
                .channelName("channelName")
                .channelFactory(factory)
                .clientConf(rawConfig)
                .ticker(ticker)
                .build();
    }
}
