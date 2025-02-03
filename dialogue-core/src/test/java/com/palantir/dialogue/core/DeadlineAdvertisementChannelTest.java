/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.Futures;
import com.palantir.deadlines.Deadlines;
import com.palantir.deadlines.Deadlines.RequestDecodingAdapter;
import com.palantir.deadlines.api.DeadlinesHttpHeaders;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.tracing.CloseableTracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeadlineAdvertisementChannelTest {

    @Test
    void adds_header_from_configured_read_timeout() {
        try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
            Duration readTimeout = Duration.ofSeconds(1);
            List<Request> requests = new ArrayList<>();
            Channel delegate = (_endpoint, request) -> {
                requests.add(request);
                return Futures.immediateCancelledFuture();
            };
            Channel channel = new DeadlineAdvertisementChannel(delegate, readTimeout);
            assertThat(channel.execute(TestEndpoint.GET, Request.builder().build()))
                    .isCancelled();

            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.headerParams().keySet())
                        .singleElement()
                        .isEqualTo(DeadlinesHttpHeaders.EXPECT_WITHIN);
                assertThat(request.headerParams().get(DeadlinesHttpHeaders.EXPECT_WITHIN))
                        .singleElement()
                        .satisfies(value -> {
                            double nSeconds = Double.parseDouble(value);
                            Duration parsed = Duration.ofNanos((long) (nSeconds * 1e9d));
                            assertThat(parsed).isEqualTo(readTimeout);
                        });
            });
        }
    }

    @Test
    void adds_header_from_remaining_deadline() {
        try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
            Duration readTimeout = Duration.ofSeconds(10);
            List<Request> requests = new ArrayList<>();
            Channel delegate = (_endpoint, request) -> {
                requests.add(request);
                return Futures.immediateCancelledFuture();
            };

            // set deadline state for this trace to somethign less than configured read timeout
            Request inboundRequest = Request.builder()
                    .putHeaderParams(DeadlinesHttpHeaders.EXPECT_WITHIN, "1")
                    .build();
            Deadlines.parseFromRequest(Optional.empty(), inboundRequest, Decoder.INSTANCE);

            Channel channel = new DeadlineAdvertisementChannel(delegate, readTimeout);
            assertThat(channel.execute(TestEndpoint.GET, Request.builder().build()))
                    .isCancelled();

            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.headerParams().keySet())
                        .singleElement()
                        .isEqualTo(DeadlinesHttpHeaders.EXPECT_WITHIN);
                assertThat(request.headerParams().get(DeadlinesHttpHeaders.EXPECT_WITHIN))
                        .singleElement()
                        .satisfies(value -> {
                            double nSeconds = Double.parseDouble(value);
                            Duration parsed = Duration.ofNanos((long) (nSeconds * 1e9d));
                            Duration maxAllowed = Duration.ofSeconds(1);
                            assertThat(parsed).isLessThanOrEqualTo(maxAllowed);
                        });
            });
        }
    }

    @Test
    void avoids_sending_zero_deadline() {
        try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
            Duration readTimeout = Duration.ZERO;
            List<Request> requests = new ArrayList<>();
            Channel delegate = (_endpoint, request) -> {
                requests.add(request);
                return Futures.immediateCancelledFuture();
            };
            Channel channel = new DeadlineAdvertisementChannel(delegate, readTimeout);
            assertThat(channel.execute(TestEndpoint.GET, Request.builder().build()))
                    .isCancelled();

            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.headerParams().keySet())
                        .singleElement()
                        .isEqualTo(DeadlinesHttpHeaders.EXPECT_WITHIN);
                assertThat(request.headerParams().get(DeadlinesHttpHeaders.EXPECT_WITHIN))
                        .singleElement()
                        .satisfies(value -> {
                            double nSeconds = Double.parseDouble(value);
                            Duration parsed = Duration.ofNanos((long) (nSeconds * 1e9d));
                            assertThat(parsed).isGreaterThan(Duration.ZERO);
                        });
            });
        }
    }

    private enum Decoder implements RequestDecodingAdapter<Request> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Request request, String headerName) {
            return request.headerParams().get(headerName).stream().findFirst();
        }
    }
}
