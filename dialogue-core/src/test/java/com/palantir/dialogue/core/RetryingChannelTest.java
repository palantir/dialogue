/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RetryingChannelTest {
    private static final TestResponse EXPECTED_RESPONSE = new TestResponse();
    private static final ListenableFuture<Response> SUCCESS = Futures.immediateFuture(EXPECTED_RESPONSE);
    private static final ListenableFuture<Response> FAILED =
            Futures.immediateFailedFuture(new IllegalArgumentException());
    private static final TestEndpoint ENDPOINT = new TestEndpoint();
    private static final Request REQUEST = Request.builder().build();

    @Mock
    private Channel channel;

    private RetryingChannel retryer;

    @Before
    public void before() {
        retryer = new RetryingChannel(channel, 3);
    }

    @Test
    public void testNoFailures() throws ExecutionException, InterruptedException {
        when(channel.execute(any(), any())).thenReturn(SUCCESS);

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesUpToMaxRetries() throws ExecutionException, InterruptedException {
        when(channel.execute(any(), any()))
                .thenReturn(FAILED)
                .thenReturn(FAILED)
                .thenReturn(SUCCESS);

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesMax() {
        when(channel.execute(any(), any())).thenReturn(FAILED);

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThatThrownBy(response::get).hasCauseInstanceOf(IllegalArgumentException.class);
        verify(channel, times(3)).execute(ENDPOINT, REQUEST);
    }

    @Test
    public void retries_429s() {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(429);
        when(channel.execute(any(), any())).thenReturn(Futures.immediateFuture(mockResponse));

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThatThrownBy(response::get)
                .hasMessageContaining("Retries exhausted")
                .hasCauseInstanceOf(RuntimeException.class);
        verify(channel, times(3)).execute(ENDPOINT, REQUEST);
    }

    @Test
    public void retries_503s() {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(503);
        when(channel.execute(any(), any())).thenReturn(Futures.immediateFuture(mockResponse));

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThatThrownBy(response::get)
                .hasMessageContaining("Retries exhausted")
                .hasCauseInstanceOf(RuntimeException.class);
        verify(channel, times(3)).execute(ENDPOINT, REQUEST);
    }

    @Test
    public void response_bodies_are_closed() throws Exception {
        Response response1 = mockResponse(503);
        Response response2 = mockResponse(503);
        Response eventualSuccess = mockResponse(200);

        when(channel.execute(any(), any()))
                .thenReturn(Futures.immediateFuture(response1))
                .thenReturn(Futures.immediateFuture(response2))
                .thenReturn(Futures.immediateFuture(eventualSuccess));

        ListenableFuture<Response> response = retryer.execute(ENDPOINT, REQUEST);
        assertThat(response.get(1, TimeUnit.SECONDS).code()).isEqualTo(200);

        verify(response1, times(1)).close();
        verify(response2, times(1)).close();
    }

    private static Response mockResponse(int status) {
        Response response = mock(Response.class);
        when(response.code()).thenReturn(status);
        return response;
    }

    private static final class TestResponse implements Response {
        @Override
        public InputStream body() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return 200;
        }

        @Override
        public Map<String, List<String>> headers() {
            return ImmutableMap.of();
        }

        @Override
        public void close() {}
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
