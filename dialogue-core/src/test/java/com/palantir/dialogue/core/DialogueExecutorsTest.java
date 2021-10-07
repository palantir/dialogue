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

import com.google.common.util.concurrent.Runnables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

final class DialogueExecutorsTest {
    private static final String THREAD_PREFIX = "dialogue-scheduler-";

    @Test
    void testThreadTimeout() throws Exception {
        ScheduledExecutorService exec = DialogueExecutors.newSharedSingleThreadScheduler(
                new ThreadFactoryBuilder()
                        .setNameFormat(THREAD_PREFIX + "%d")
                        .setDaemon(true)
                        .build(),
                Duration.ofMillis(100));
        try {
            assertThat(countExecutorThreads()).isEqualTo(0);
            exec.schedule(Runnables.doNothing(), 1, TimeUnit.SECONDS).cancel(true);
            assertThat(countExecutorThreads()).isEqualTo(1);

            Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(countExecutorThreads())
                    .as("Created threads should time out")
                    .isEqualTo(0));

            // Test scheduling beyond the timeout
            AtomicInteger counter = new AtomicInteger();
            ScheduledFuture<Integer> scheduledFuture = exec.schedule(counter::incrementAndGet, 1, TimeUnit.SECONDS);
            assertThat(countExecutorThreads()).isEqualTo(1);
            assertThat(scheduledFuture.get(1500, TimeUnit.MILLISECONDS)).isEqualTo(1);

            Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(countExecutorThreads())
                    .as("Created threads should time out")
                    .isEqualTo(0));
        } finally {
            exec.shutdownNow();
            assertThat(exec.awaitTermination(1, TimeUnit.SECONDS))
                    .as("Executor failed to stop")
                    .isTrue();
        }
    }

    private long countExecutorThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith(THREAD_PREFIX))
                .count();
    }
}
