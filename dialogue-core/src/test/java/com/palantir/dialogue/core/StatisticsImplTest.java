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

import static org.mockito.Mockito.mock;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class StatisticsImplTest {

    Endpoint endpoint = mock(Endpoint.class);
    Request request = mock(Request.class);

    Statistics.Upstream node1 = ImmutableUpstream.of("node1");
    Statistics.Upstream node2 = ImmutableUpstream.of("node2");

    // @Test
    // public void when_both_nodes_are_borked() {
    //     StatisticsImpl stats = new StatisticsImpl(() -> ImmutableList.of(node1, node2), Ticker.systemTicker());
    //
    //     stats.recordStart(node1, endpoint, request)
    //             .recordComplete(Optional.of(response(500, "1.56.0")), Optional.empty());
    //
    //     stats.recordStart(node2, endpoint, request)
    //             .recordComplete(Optional.of(response(500, "1.56.1")), Optional.empty());
    //
    //     Optional<Statistics.Upstream> upstream = stats.selectBestUpstreamFor(endpoint, request);
    //     assertThat(upstream).isEmpty();
    // }

    @Test
    public void foo() throws InterruptedException {
        ExponentiallyDecayingReservoir reservoir = new ExponentiallyDecayingReservoir();
        System.out.println(Math.floor(reservoir.getSnapshot().getMean() * 100) / 100);
    }

    private Response response(int status, String version) {
        return new Response() {
            @Override
            public InputStream body() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public int code() {
                return status;
            }

            @Override
            public Map<String, List<String>> headers() {
                return ImmutableMap.of("server", ImmutableList.of("foundry-catalog/" + version));
            }
        };
    }
}
