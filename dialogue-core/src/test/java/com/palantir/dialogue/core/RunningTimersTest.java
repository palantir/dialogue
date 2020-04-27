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
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunningTimersTest {

    Ticker ticker = mock(Ticker.class);

    @ParameterizedTest
    @EnumSource(Impl.class)
    void can_track_one_timer(Impl impl) {
        RunningTimers timer = getTimer(impl);

        setTicker(0L);
        RunningTimers.RunningTimer running = timer.start();

        setTicker(10L);
        assertThat(timer.getRunningNanos()).isEqualTo(10L);

        running.stop();
        assertThat(timer.getRunningNanos()).isEqualTo(0L);
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void can_track_multiple_timers(Impl impl) {
        RunningTimers timer = getTimer(impl);

        setTicker(0L);
        timer.start();

        setTicker(10L);
        timer.start();
        assertThat(timer.getRunningNanos()).isEqualTo(10L);

        setTicker(20L);
        assertThat(timer.getRunningNanos()).isEqualTo(30L);
    }

    private void setTicker(long nanos) {
        when(ticker.read()).thenReturn(nanos);
    }

    enum Impl {
        REFERENCE,
        FAST;
    }

    private RunningTimers getTimer(Impl impl) {
        switch (impl) {
            case REFERENCE:
                return new ReferenceImpl(ticker);
            case FAST:
                return new RunningTimersLongImpl(ticker);
        }
        throw new IllegalArgumentException();
    }

    static final class ReferenceImpl implements RunningTimers {

        private final Ticker ticker;
        private final Set<Timer> timers = new HashSet<>();

        ReferenceImpl(Ticker ticker) {
            this.ticker = ticker;
        }

        @Override
        public RunningTimer start() {
            long currentNanos = ticker.read();
            Timer newTimer = new Timer(currentNanos);
            timers.add(newTimer);
            return newTimer;
        }

        @Override
        public long getRunningNanos() {
            long now = ticker.read();
            return timers.stream().mapToLong(timer -> now - timer.startTime).sum();
        }

        @Override
        public int getCount() {
            return timers.size();
        }

        public final class Timer implements RunningTimer {
            private long startTime;

            Timer(long startTime) {
                this.startTime = startTime;
            }

            @Override
            public void stop() {
                timers.remove(this);
            }
        }
    }
}
