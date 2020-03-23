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
package com.palantir.dialogue.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TagKey;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.awaitility.Awaitility;
import org.junit.Test;

public class BlockingChannelAdapterTest {

    private static final Response stubResponse = new Response() {
        @Override
        public InputStream body() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return 200;
        }

        @Override
        public ListMultimap<String, String> headers() {
            return ImmutableListMultimap.of();
        }

        @Override
        public void close() {}

        @Override
        public <T> Optional<T> getTag(TagKey<T> tagKey) {
            return Optional.empty();
        }
    };

    private static final Endpoint stubEndpoint = new Endpoint() {

        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
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
    };

    @Test
    public void testSuccessful() {
        CountDownLatch latch = new CountDownLatch(1);
        Channel channel = BlockingChannelAdapter.of((_endpoint, _request) -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return stubResponse;
        });
        ListenableFuture<Response> result =
                channel.execute(stubEndpoint, Request.builder().build());
        assertThat(result).isNotDone();
        latch.countDown();
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(result).isDone();
            assertThat(result.get()).isSameAs(stubResponse);
        });
    }

    @Test
    public void testFailure() {
        Channel channel = BlockingChannelAdapter.of((_endpoint, _request) -> {
            throw new SafeRuntimeException("expected");
        });
        ListenableFuture<Response> result =
                channel.execute(stubEndpoint, Request.builder().build());
        Awaitility.waitAtMost(Duration.ofSeconds(3)).until(result::isDone);
        assertThatThrownBy(result::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseExactlyInstanceOf(SafeRuntimeException.class)
                .hasRootCauseMessage("expected");
    }
}
