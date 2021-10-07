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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Internal API which shouldn't be used outside of dialogue. */
public final class DialogueExecutors {

    private static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(10);

    /**
     * Create an executor which allows all threads to exit after a timeout has elapsed, this prevents thread leakage
     * when dialogue is packaged as a dynamically loaded plugin.
     */
    public static ScheduledExecutorService newSharedSingleThreadScheduler(ThreadFactory threadFactory) {
        return newSharedSingleThreadScheduler(threadFactory, DEFAULT_KEEP_ALIVE);
    }

    @VisibleForTesting
    @SuppressWarnings("DangerousThreadPoolExecutorUsage")
    static ScheduledExecutorService newSharedSingleThreadScheduler(
            ThreadFactory threadFactory, Duration keepAliveTime) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, threadFactory);
        // Core threads must be allowed to time out to allow garbage collection
        executor.allowCoreThreadTimeOut(true);
        executor.setKeepAliveTime(keepAliveTime.toNanos(), TimeUnit.NANOSECONDS);
        // remove-on-cancel allows heavy cancellation without risk of OOM waiting for
        // the originally scheduled deadline. This allows the keep-alive clock to
        // begin counting when futures are canceled rather than after the scheduled
        // task has completed.
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private DialogueExecutors() {}
}
