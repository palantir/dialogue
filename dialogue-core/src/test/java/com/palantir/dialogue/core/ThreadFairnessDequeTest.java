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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThreadFairnessDequeTest {
    private final ThreadFairnessDeque<Integer> queue = new ThreadFairnessDeque<>();

    @Test
    public void testPrioritizesPerThread() throws InterruptedException {
        queue.addLast(1);
        enqueueWithNewThread(2, 3, 4);
        enqueueWithNewThread(5, 6, 7);
        queue.addLast(8);
        assertThat(dequeue()).containsExactly(1, 2, 5, 8, 3, 6, 4, 7);
    }

    @Test
    public void testThrowsIfEmpty() {
        assertThat(queue.poll()).isNull();
    }

    @Test
    public void testFifoEvenWhileTotallyDequeued() throws InterruptedException {
        queue.addLast(1);
        enqueueWithNewThread(2, 3);
        assertThat(queue.poll()).isEqualTo(1);
        assertThat(queue.poll()).isEqualTo(2);
        queue.addLast(4);
        assertThat(queue.poll()).isEqualTo(3);
        assertThat(queue.poll()).isEqualTo(4);
    }

    private List<Integer> dequeue() {
        List<Integer> result = new ArrayList<>();
        while (queue.size() > 0) {
            result.add(queue.poll());
        }
        return result;
    }

    private void enqueueWithNewThread(int... numbers) throws InterruptedException {
        Thread thread = new Thread(() -> Arrays.stream(numbers).forEach(queue::addLast));
        thread.start();
        thread.join();
    }
}
