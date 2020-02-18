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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PinUntilErrorChannelTest {

    private LimitedChannel channel1 = mock(LimitedChannel.class);
    private LimitedChannel channel2 = mock(LimitedChannel.class);
    private Ticker clock = mock(Ticker.class);

    private ImmutableList<LimitedChannel> channels = ImmutableList.of(channel1, channel2);
    private PinUntilErrorChannel pinUntilErrorWithoutReshuffle;
    private PinUntilErrorChannel pinUntilError;

    @Before
    public void before() {
        pinUntilErrorWithoutReshuffle = new PinUntilErrorChannel(channels, new Random(12345L));
        pinUntilError = new PinUntilErrorChannel(channels, new Random(12893712L), clock);
    }

    @Test
    public void channels_are_shuffled_initially_and_successful_requests_stay_on_channel() {
        setResponse(channel1, 200);
        setResponse(channel2, 204);

        assertThat(getCode(pinUntilErrorWithoutReshuffle)).isEqualTo(204);
        assertThat(getCode(pinUntilErrorWithoutReshuffle)).isEqualTo(204);
        assertThat(getCode(pinUntilErrorWithoutReshuffle)).isEqualTo(204);
    }

    @Test
    public void various_error_status_codes_cause_node_switch() {
        for (int errorStatus = 300; errorStatus < 600; errorStatus++) {
            before();
            setResponse(channel1, 100);
            setResponse(channel2, 204);

            assertThat(IntStream.range(0, 6).map(number -> getCode(pinUntilErrorWithoutReshuffle)))
                    .describedAs("Should be locked on to channel2 initially")
                    .contains(204, 204, 204, 204, 204, 204);

            setResponse(channel2, errorStatus);

            assertThat(IntStream.range(0, 6).map(number -> getCode(pinUntilErrorWithoutReshuffle)))
                    .describedAs("A single error code should switch us to channel 1")
                    .contains(errorStatus, 100, 100, 100, 100, 100);
        }
    }

    @Test
    public void reshuffle_happens_roughly_every_10_mins() {
        setResponse(channel1, 100);
        setResponse(channel2, 204);

        assertThat(IntStream.range(0, 6).map(number -> getCode(pinUntilError)))
                .describedAs("First batch on channel1")
                .contains(100, 100, 100, 100, 100, 100);

        when(clock.read()).thenReturn(Duration.ofMinutes(11).toNanos());
        assertThat(IntStream.range(0, 6).map(number -> getCode(pinUntilError)))
                .describedAs("Second batch: reshuffle gave us channel2")
                .contains(204, 204, 204, 204, 204, 204);

        when(clock.read()).thenReturn(Duration.ofMinutes(22).toNanos());
        assertThat(IntStream.range(0, 6).map(number -> getCode(pinUntilError)))
                .describedAs("Third batch: reshuffle gave us channel2 again")
                .contains(204, 204, 204, 204, 204, 204);

        when(clock.read()).thenReturn(Duration.ofMinutes(33).toNanos());
        assertThat(IntStream.range(0, 6).map(number -> getCode(pinUntilError)))
                .describedAs("Fourth batch: reshuffle gave us channel1")
                .contains(100, 100, 100, 100, 100, 100);
    }

    @Test
    public void out_of_order_responses_dont_cause_us_to_switch_channel() {
        setResponse(channel1, 100);

        // should all be on channel2 initially
        SettableFuture<Response> future1 = SettableFuture.create();
        SettableFuture<Response> future2 = SettableFuture.create();
        when(channel2.maybeExecute(any(), any()))
                .thenReturn(Optional.of(future1))
                .thenReturn(Optional.of(future2));

        future2.set(response(500));
        assertThat(getCode(pinUntilError))
                .describedAs("A single 500 moved us to channel1")
                .isEqualTo(100);

        future1.set(response(500));
        assertThat(getCode(pinUntilError))
                .describedAs("We're still on channel1, as an older 500 doesn't provide "
                        + "signal about the current channel")
                .isEqualTo(100);
    }

    private static int getCode(PinUntilErrorChannel channel) {
        try {
            ListenableFuture<Response> future = channel.maybeExecute(null, null).get();
            Response response = future.get(1, TimeUnit.MILLISECONDS);
            return response.code();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setResponse(LimitedChannel mockChannel, int status) {
        Mockito.clearInvocations(mockChannel);
        Mockito.reset(mockChannel);
        Response resp = response(status);
        when(mockChannel.maybeExecute(any(), any())).thenReturn(Optional.of(Futures.immediateFuture(resp)));
    }

    private static Response response(int status) {
        Response resp = mock(Response.class);
        when(resp.code()).thenReturn(status);
        return resp;
    }
}
