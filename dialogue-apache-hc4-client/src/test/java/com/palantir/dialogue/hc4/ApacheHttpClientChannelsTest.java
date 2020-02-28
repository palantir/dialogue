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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.AbstractChannelTest;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.UrlBuilder;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.Test;

public final class ApacheHttpClientChannelsTest extends AbstractChannelTest {

    @Override
    protected Channel createChannel(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config);
    }

    @Test
    public void close_works() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://foo");

        Channel channel;
        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf)) {

            channel = ApacheHttpClientChannels.createSingleUri("http://foo", client);
            ListenableFuture<Response> response =
                    channel.execute(new TestEndpoint(), Request.builder().build());
            assertThatThrownBy(() -> Futures.getUnchecked(response)).hasCauseInstanceOf(UnknownHostException.class);
        }

        ListenableFuture<Response> again =
                channel.execute(new TestEndpoint(), Request.builder().build());
        assertThatThrownBy(() -> again.get()).hasMessageContaining("Connection pool shut down");
    }

    @Test
    public void metrics() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://unused");

        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf)) {

            Channel channel = ApacheHttpClientChannels.createSingleUri("http://neverssl.com", client);
            ListenableFuture<Response> future =
                    channel.execute(new TestEndpoint(), Request.builder().build());

            try (Response response = Futures.getUnchecked(future)) {
                assertThat(response.code()).isEqualTo(200);

                assertThat(client.getNumRoutes()).describedAs("routes").isEqualTo(1);
                assertThat(client.getPoolStats().getAvailable())
                        .describedAs("available")
                        .isEqualTo(0);
                assertThat(client.getPoolStats().getLeased())
                        .describedAs("leased")
                        .isEqualTo(1);
            }

            assertThat(client.getNumRoutes())
                    .describedAs("routes after response closed")
                    .isEqualTo(1);
            assertThat(client.getPoolStats().getAvailable())
                    .describedAs("available after response closed")
                    .isEqualTo(0);
            assertThat(client.getPoolStats().getLeased())
                    .describedAs("leased after response closed")
                    .isEqualTo(0);
        }
    }

    private static final class TestEndpoint implements Endpoint {
        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "service";
        }

        @Override
        public String endpointName() {
            return "endpoint";
        }

        @Override
        public String version() {
            return "1.0.0";
        }
    }
}
