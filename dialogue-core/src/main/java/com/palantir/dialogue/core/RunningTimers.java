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

interface RunningTimers {

    /**
     * Starts a new conceptual 'timer', whose running time will be immediately reflected in the
     * {@link #getRunningNanos()} function.
     */
    RunningTimer start();

    /** Returns the elapsed time summed across all running timers. */
    long getRunningNanos();

    /** The number of running timers active right now. */
    int getCount();

    interface RunningTimer {
        /**
         * Call this exactly once to declare that we are done with this timer and it should no longer be reflected in
         * the {@link #getRunningNanos()} sum.
         */
        void stop();
    }
}
