/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class RetryOtherValidatingChannelTest {

    @Mock
    private Channel delegate;

    @Mock
    private Consumer<String> failureReporter;

    @Test
    public void testReportsUnparseableHost() {
        String retryOtherUri = "not-a-uri";
        execute(retryOtherUri);
        verify(failureReporter).accept(retryOtherUri);
    }

    @Test
    public void testReportsUnknownHost() {
        String retryOtherUri = "https://host2.palantir.dev:9090/service/api";
        execute(retryOtherUri);
        verify(failureReporter).accept(retryOtherUri);
    }

    @Test
    public void testDoesNotReportCorrectHostButDifferentPath() {
        execute("https://host1.palantir.dev:9090/service-1/api");
        verifyNoInteractions(failureReporter);
    }

    @Test
    public void testDoesNotReportCorrectHostButDifferentPort() {
        execute("https://host1.palantir.dev:9091/service/api");
        verifyNoInteractions(failureReporter);
    }

    @Test
    public void testDoesNotReportMissingLocation() {
        execute(null);
        verifyNoInteractions(failureReporter);
    }

    private void execute(@Nullable String retryOtherUri) {
        RetryOtherValidatingChannel channel = new RetryOtherValidatingChannel(
                delegate,
                ImmutableSet.of(
                                "https://host3.palantir.dev:9090/service/api",
                                "https://host1.palantir.dev:9090/service/api")
                        .stream()
                        .map(RetryOtherValidatingChannel::strictParseHost)
                        .collect(Collectors.toSet()),
                failureReporter);

        Request request = Request.builder().build();
        TestResponse response = TestResponse.withBody(null).code(308);
        if (retryOtherUri != null) {
            response = response.withHeader(HttpHeaders.LOCATION, retryOtherUri);
        }

        when(delegate.execute(TestEndpoint.GET, request)).thenReturn(Futures.immediateFuture(response));
        assertThat(channel.execute(TestEndpoint.GET, request))
                .succeedsWithin(Duration.ZERO)
                .isEqualTo(response);
    }

    @Test
    void parsesMeshUris() {
        assertThat(RetryOtherValidatingChannel.maybeParseHost("mesh-http://localhost:1234/api"))
                .isEqualTo("localhost");
    }

    @Test
    void parsesStandardUris() {
        assertThat(RetryOtherValidatingChannel.maybeParseHost("https://host.palantir.com:1234/api"))
                .isEqualTo("host.palantir.com");
    }

    @Test
    void parsesStandardUrisWithoutPort() {
        assertThat(RetryOtherValidatingChannel.maybeParseHost("https://host.palantir.com/api"))
                .isEqualTo("host.palantir.com");
    }
}
