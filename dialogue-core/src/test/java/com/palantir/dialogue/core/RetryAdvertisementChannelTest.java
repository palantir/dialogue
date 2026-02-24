/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryAdvertisementChannelTest {

    @Test
    void addsClientCanRetryHeader() {
        List<Request> requests = new ArrayList<>();
        EndpointChannel delegate = request -> {
            requests.add(request);
            return Futures.immediateCancelledFuture();
        };
        EndpointChannel channel = new RetryAdvertisementChannel(delegate);
        assertThat(channel.execute(Request.builder().build())).isCancelled();

        assertThat(requests)
                .singleElement()
                .satisfies(request -> assertThat(request.headerParams())
                        .isEqualTo(
                                ImmutableListMultimap.of(RetryAdvertisementChannel.CLIENT_CAN_RETRY_HEADER, "true")));
    }

    @Test
    void clientCanRetryHeaderAlreadyExists() {
        List<Request> requests = new ArrayList<>();
        EndpointChannel delegate = request -> {
            requests.add(request);
            return Futures.immediateCancelledFuture();
        };
        EndpointChannel channel = new RetryAdvertisementChannel(delegate);
        Request inputRequest = Request.builder()
                .putHeaderParams(RetryAdvertisementChannel.CLIENT_CAN_RETRY_HEADER, "false")
                .build();
        assertThat(channel.execute(inputRequest)).isCancelled();

        // The existing header should not be mutated
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.headerParams())
                    .isEqualTo(ImmutableListMultimap.of(RetryAdvertisementChannel.CLIENT_CAN_RETRY_HEADER, "false"));
            assertThat(request)
                    .as("the channel should not create a new request object unnecessarily")
                    .isSameAs(inputRequest);
        });
    }
}
