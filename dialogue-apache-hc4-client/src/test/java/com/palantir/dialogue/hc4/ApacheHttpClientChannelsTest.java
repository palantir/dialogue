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
package com.palantir.dialogue.hc4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.google.common.collect.MoreCollectors;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.AbstractChannelTest;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ApacheHttpClientChannelsTest extends AbstractChannelTest {

    @Override
    protected Channel createChannel(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config);
    }

    @Test
    public void close_doesnt_fail_inflight_requests() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://foo");

        Channel channel;
        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "client")) {

            channel = ApacheHttpClientChannels.createSingleUri("http://foo", client);
            ListenableFuture<Response> response =
                    channel.execute(TestEndpoint.POST, Request.builder().build());
            assertThatThrownBy(() -> Futures.getUnchecked(response)).hasCauseInstanceOf(UnknownHostException.class);
        }

        ListenableFuture<Response> again =
                channel.execute(TestEndpoint.POST, Request.builder().build());
        assertThatThrownBy(() -> Futures.getUnchecked(again)).hasCauseInstanceOf(UnknownHostException.class);
    }

    @Test
    public void metrics() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://unused");

        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "testClient")) {

            Channel channel = ApacheHttpClientChannels.createSingleUri("http://neverssl.com", client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());

            TaggedMetricRegistry metrics = conf.taggedMetricRegistry();
            try (Response response = Futures.getUnchecked(future)) {
                assertThat(response.code()).isEqualTo(200);

                assertThat(poolGaugeValue(metrics, "testClient", "idle"))
                        .describedAs("available")
                        .isZero();
                assertThat(poolGaugeValue(metrics, "testClient", "leased"))
                        .describedAs("leased")
                        .isEqualTo(1L);
            }

            assertThat(poolGaugeValue(metrics, "testClient", "idle"))
                    .describedAs("available after response closed")
                    .isZero();
            assertThat(poolGaugeValue(metrics, "testClient", "leased"))
                    .describedAs("leased after response closed")
                    .isZero();
        }
    }

    private long poolGaugeValue(TaggedMetricRegistry metrics, String clientName, String state) {
        Metric gauge = metrics.getMetrics().entrySet().stream()
                .filter(entry -> entry.getKey().safeName().equals("dialogue.client.pool.size"))
                .filter(entry -> clientName.equals(entry.getKey().safeTags().get("client-name")))
                .filter(entry -> state.equals(entry.getKey().safeTags().get("state")))
                .map(Map.Entry::getValue)
                .collect(MoreCollectors.onlyElement());
        assertThat(gauge).isInstanceOf(Gauge.class);
        Object value = ((Gauge<?>) gauge).getValue();
        assertThat(value).isInstanceOf(Long.class);
        return (long) value;
    }
}
