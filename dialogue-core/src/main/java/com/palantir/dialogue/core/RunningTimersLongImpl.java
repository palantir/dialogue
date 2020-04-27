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

import com.github.benmanes.caffeine.cache.Ticker;

final class RunningTimersLongImpl implements RunningTimers {

    private final Ticker ticker;

    // TODO(dfox): binpack both of these into an AtomicLong
    private long fromBeginningOfTime = 0L;
    private int count = 0;

    RunningTimersLongImpl(Ticker ticker) {
        this.ticker = ticker;
    }

    @Override
    public RunningTimer start() {
        // TODO(dfox): store a reference point so we can rescale fromBeginningOfTime
        long now = ticker.read();
        fromBeginningOfTime += now;
        count += 1;
        return new Timer(now);
    }

    @Override
    public long getRunningNanos() {
        long now = ticker.read();
        return (now * count) - fromBeginningOfTime;
    }

    @Override
    public int getCount() {
        return count;
    }

    private class Timer implements RunningTimer {
        private final long creationTime;

        Timer(long creationTime) {
            this.creationTime = creationTime;
        }

        @Override
        public void stop() {
            count -= 1;
            fromBeginningOfTime -= creationTime;
        }
    }
}
