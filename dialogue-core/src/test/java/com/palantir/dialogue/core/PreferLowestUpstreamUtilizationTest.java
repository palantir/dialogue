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
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import org.junit.Test;

public class PreferLowestUpstreamUtilizationTest {

    Endpoint endpoint = mock(Endpoint.class);
    Request request = mock(Request.class);

    Statistics.Upstream node1 = ImmutableUpstream.of("node1");
    Statistics.Upstream node2 = ImmutableUpstream.of("node2");
    ImmutableList<Statistics.Upstream> upstreams = ImmutableList.of(node1, node2);

    PreferLowestUpstreamUtilization foo = construct();

    @Test
    public void pick_lowest_utilization() {
        foo.recordStart(node1, endpoint, request);
        foo.recordStart(node1, endpoint, request);
        foo.recordStart(node1, endpoint, request);

        assertThat(foo.getBest(endpoint)).hasValue(node2);
    }

    @Test
    public void pick_first_when_all_empty() {
        // TODO(dfox): should there be a way of expressing 'no preference' when there are zero active reqs? might be
        //  helpful when combining with other strategies
        assertThat(foo.getBest(endpoint)).hasValue(node1);
    }

    @Test
    public void completed_requests_are_recorded_properly() {
        foo.recordStart(node2, endpoint, request);
        foo.recordStart(node1, endpoint, request).recordComplete(null, null);
        foo.recordStart(node1, endpoint, request).recordComplete(null, null);
        foo.recordStart(node1, endpoint, request).recordComplete(null, null);

        assertThat(foo.getBest(endpoint)).hasValue(node1);
    }

    private PreferLowestUpstreamUtilization construct() {
        return new PreferLowestUpstreamUtilization(() -> upstreams);
    }
}
