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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
final class ThreadFairnessQueue<T> {

    @GuardedBy("this")
    private final Map<Long, Deque<T>> queueByThreadId = new LinkedHashMap<>();

    private final Function<Long, Deque<T>> CREATE = threadId -> new ArrayDeque<>(2);
    private final AtomicInteger size = new AtomicInteger();
    private final int maxSize;

    ThreadFairnessQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    int size() {
        return size.get();
    }

    boolean offerFirst(T element) {
        int newSize = size.incrementAndGet();
        if (newSize > maxSize) {
            size.decrementAndGet();
            return false;
        }

        synchronized (this) {
            long threadId = Thread.currentThread().getId();
            return queue(threadId).offerFirst(element);
        }
    }

    boolean offerLast(T element) {
        int newSize = size.incrementAndGet();
        if (newSize > maxSize) {
            size.decrementAndGet();
            return false;
        }

        synchronized (this) {
            long threadId = Thread.currentThread().getId();
            return queue(threadId).offerLast(element);
        }
    }

    @Nullable
    T poll() {
        if (size.get() > 0) {
            synchronized (this) {
                Map.Entry<Long, Deque<T>> entry = pickNextQueue();
                T result = entry.getValue().poll();
                size.decrementAndGet();

                if (!entry.getValue().isEmpty()) {
                    // put it back in the map so we come round to process the rest of its requests
                    queueByThreadId.put(entry.getKey(), entry.getValue());
                }
                return result;
            }
        } else {
            return null;
        }
    }

    private synchronized Map.Entry<Long, Deque<T>> pickNextQueue() {
        // relies on LinkedHashMap returning things in the same order keys were inserted
        Iterator<Map.Entry<Long, Deque<T>>> iterator =
                queueByThreadId.entrySet().iterator();
        Map.Entry<Long, Deque<T>> result = iterator.next();
        iterator.remove();
        return result;
    }

    private synchronized Deque<T> queue(long threadId) {
        return queueByThreadId.computeIfAbsent(threadId, CREATE);
    }
}
