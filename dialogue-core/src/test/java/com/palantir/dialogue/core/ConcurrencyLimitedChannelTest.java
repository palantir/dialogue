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

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConcurrencyLimitedChannelTest {

    // @Mock
    // private Endpoint endpoint;
    //
    // @Mock
    // private Request request;
    //
    // @Mock
    // private Channel delegate;
    //
    // @Mock
    // private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter mockLimiter;
    //
    // @Spy
    // private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit =
    //         new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter().acquire().get();
    //
    // @Mock
    // private Response response;
    //
    // private final TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
    // private ConcurrencyLimitedChannel channel;
    // private SettableFuture<Response> responseFuture;
    //
    // @BeforeEach
    // public void before() {
    //     channel = new ConcurrencyLimitedChannel(
    //             new ChannelToLimitedChannelAdapter(delegate), mockLimiter, "channel", 0, metrics);
    //
    //     responseFuture = SettableFuture.create();
    //     lenient().when(delegate.execute(endpoint, request)).thenReturn(responseFuture);
    // }
    //
    // @Test
    // public void testLimiterAvailable_successfulRequest() {
    //     mockLimitAvailable();
    //     mockResponseCode(200);
    //
    //     assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    //     verify(permit).success();
    // }
    //
    // @Test
    // public void testLimiterAvailable_429isDropped() {
    //     mockLimitAvailable();
    //     mockResponseCode(429);
    //
    //     assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    //     verify(permit).dropped();
    // }
    //
    // @Test
    // public void testLimiterAvailable_runtimeExceptionIsIgnored() {
    //     mockLimitAvailable();
    //     responseFuture.setException(new IllegalStateException());
    //
    //     assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    //     verify(permit).ignore();
    // }
    //
    // @Test
    // public void testLimiterAvailable_ioExceptionIsDropped() {
    //     mockLimitAvailable();
    //     responseFuture.setException(new SafeIoException("failure"));
    //
    //     assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    //     verify(permit).dropped();
    // }
    //
    // @Test
    // public void testUnavailable() {
    //     mockLimitUnavailable();
    //
    //     assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
    //     verifyNoMoreInteractions(permit);
    // }
    //
    // @Test
    // public void testWithDefaultLimiter() {
    //     channel = new ConcurrencyLimitedChannel(
    //             new ChannelToLimitedChannelAdapter(delegate),
    //             ConcurrencyLimitedChannel.createLimiter(),
    //             "channel",
    //             0,
    //             metrics);
    //
    //     assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    // }
    //
    // @Test
    // void testGauges() {
    //     when(mockLimiter.getLimit()).thenReturn(21D);
    //
    //     assertThat(getMax()).isEqualTo(21D);
    // }
    //
    // private void mockResponseCode(int code) {
    //     when(response.code()).thenReturn(code);
    //     responseFuture.set(response);
    // }
    //
    // private void mockLimitAvailable() {
    //     when(mockLimiter.acquire()).thenReturn(Optional.of(permit));
    // }
    //
    // private void mockLimitUnavailable() {
    //     when(mockLimiter.acquire()).thenReturn(Optional.empty());
    // }
    //
    // @SuppressWarnings("unchecked")
    // private Number getMax() {
    //     Gauge<Number> metric = (Gauge<Number>) metrics.getMetrics().entrySet().stream()
    //             .filter(entry -> entry.getKey().safeName().equals("dialogue.concurrencylimiter.max"))
    //             .findFirst()
    //             .get()
    //             .getValue();
    //     return metric.getValue();
    // }
}
