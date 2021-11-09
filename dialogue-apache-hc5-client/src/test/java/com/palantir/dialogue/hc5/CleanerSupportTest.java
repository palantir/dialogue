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

package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class CleanerSupportTest {

    @Test
    void testCleaner() {
        AtomicInteger counter = new AtomicInteger();
        CleanerSupport.register(new byte[1024 * 1024], counter::incrementAndGet);
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            attemptToGarbageCollect();
            assertThat(counter).hasValue(1);
        });
    }

    private static void attemptToGarbageCollect() {
        // Create some garbage to entice the collector
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (baos.toString(StandardCharsets.UTF_8).length() < 4096) {
            byte[] buf = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            baos.write(buf, 0, buf.length);
        }
        // System.gc is disabled in some environments, so it alone cannot be relied upon.
        System.gc();
    }
}
