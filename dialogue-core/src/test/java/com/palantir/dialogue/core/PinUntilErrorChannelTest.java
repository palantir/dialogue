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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PinUntilErrorChannelTest {

    @Mock
    private LimitedChannel channel1;

    @Mock
    private LimitedChannel channel2;

    @Mock
    private Ticker clock;

    private PinUntilErrorChannel pinUntilErrorWithoutReshuffle;
    private PinUntilErrorChannel pinUntilError;

    @BeforeEach
    public void before() {
        List<LimitedChannel> channels = ImmutableList.of(channel1, channel2);
        pinUntilErrorWithoutReshuffle =
                new PinUntilErrorChannel(new PinUntilErrorChannel.ConstantNodeList(channels, new Random(12345L)));
        pinUntilError = new PinUntilErrorChannel(
                new PinUntilErrorChannel.ReshufflingNodeList(channels, new Random(12893712L), clock));
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
    public void out_of_order_responses_dont_cause_us_to_switch_channel() throws Exception {
        setResponse(channel1, 100);
        setResponse(channel2, 101);
        assertThat(getCode(pinUntilError)).describedAs("On channel2 initially").isEqualTo(100);

        SettableFuture<Response> future1 = SettableFuture.create();
        SettableFuture<Response> future2 = SettableFuture.create();
        when(channel1.maybeExecute(any(), any()))
                .thenReturn(Optional.of(future1))
                .thenReturn(Optional.of(future2));

        // kick off two requests
        pinUntilError.maybeExecute(null, null).get();
        pinUntilError.maybeExecute(null, null).get();

        // second request completes before the first (i.e. out of order), but they both signify the host wass broken
        future2.set(response(500));

        assertThat(getCode(pinUntilError))
                .describedAs("A single 500 moved us to channel2")
                .isEqualTo(101);

        future1.set(response(500));

        assertThat(getCode(pinUntilError))
                .describedAs("We're still on channel2, as an older 500 doesn't mark this host as bad")
                .isEqualTo(101);
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
        lenient().when(mockChannel.maybeExecute(any(), any())).thenReturn(Optional.of(Futures.immediateFuture(resp)));
    }

    private static Response response(int status) {
        Response resp = mock(Response.class);
        lenient().when(resp.code()).thenReturn(status);
        return resp;
    }
}
