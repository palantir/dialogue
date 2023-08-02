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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class RetryOtherValidatingChannelTest {

    @Mock
    private Channel delegate;

    @Mock
    private Consumer<String> failureReporter;

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    public void testReportsUnparseableHost(int statusCode) {
        String retryOtherUri = "not-a-uri";
        execute(retryOtherUri, statusCode);
        verify(failureReporter).accept(retryOtherUri);
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    public void testReportsUnknownHost(int statusCode) {
        String retryOtherUri = "https://host2.palantir.dev:9090/service/api";
        execute(retryOtherUri, statusCode);
        verify(failureReporter).accept(retryOtherUri);
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    public void testDoesNotReportCorrectHostButDifferentPath(int statusCode) {
        execute("https://host1.palantir.dev:9090/service-1/api", statusCode);
        verifyNoInteractions(failureReporter);
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    public void testDoesNotReportCorrectHostButDifferentPort(int statusCode) {
        execute("https://host1.palantir.dev:9091/service/api", statusCode);
        verifyNoInteractions(failureReporter);
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    public void testDoesNotReportMissingLocation(int statusCode) {
        execute(null, statusCode);
        verifyNoInteractions(failureReporter);
    }

    private void execute(@Nullable String retryOtherUri, int statusCode) {
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
        TestResponse response = TestResponse.withBody(null).code(statusCode);
        if (retryOtherUri != null) {
            response = response.withHeader(HttpHeaders.LOCATION, retryOtherUri);
        }

        when(delegate.execute(TestEndpoint.GET, request)).thenReturn(Futures.immediateFuture(response));
        assertThat(channel.execute(TestEndpoint.GET, request))
                .succeedsWithin(Duration.ZERO)
                .isEqualTo(response);
    }
}
