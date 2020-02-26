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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.blocking.BlockingChannel;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.junit.Test;

public final class ApacheExceptionNormalizingChannelTest {

    @Test
    public void testConnectTimeoutException() {
        BlockingChannel channel = new ApacheExceptionNormalizingChannel((endpoint, request) -> {
            throw new ConnectTimeoutException();
        });
        assertThatThrownBy(() ->
                        channel.execute(FakeEndpoint.INSTANCE, Request.builder().build()))
                .isInstanceOf(SocketTimeoutException.class)
                .hasMessage("connect timed out")
                .hasCauseInstanceOf(ConnectTimeoutException.class);
    }

    @Test
    public void testConnectionPoolTimeoutException() {
        BlockingChannel channel = new ApacheExceptionNormalizingChannel((endpoint, request) -> {
            throw new ConnectionPoolTimeoutException();
        });
        assertThatThrownBy(() ->
                        channel.execute(FakeEndpoint.INSTANCE, Request.builder().build()))
                .isInstanceOf(SocketTimeoutException.class)
                .hasMessage("connect timed out")
                .hasCauseInstanceOf(ConnectionPoolTimeoutException.class);
    }

    private enum FakeEndpoint implements Endpoint {
        INSTANCE;

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
