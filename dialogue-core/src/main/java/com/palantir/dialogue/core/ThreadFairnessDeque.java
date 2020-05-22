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

import com.google.common.collect.Maps;
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

/**
 * This exists to avoid the failure mode of one background thread submitting thousands of requests (and getting
 * horribly rate limited) while another thread submitted just 1 request, but is now stuck behind a massive queue
 *
 * With this 'fair' implementation, we return a request from each thread in turn, so all should make progress in
 * an equal way.
 */
@ThreadSafe
final class ThreadFairnessDeque<T> {
    @SuppressWarnings("UnnecessaryLambda") // avoiding allocation
    private final Function<Long, Deque<T>> createNewQueue = threadId -> new ArrayDeque<>(2);

    @GuardedBy("this")
    private final Map<Long, Deque<T>> queueByThreadId = new LinkedHashMap<>();

    private final AtomicInteger size = new AtomicInteger();

    int size() {
        return size.get();
    }

    synchronized void addLast(T element) {
        size.incrementAndGet();
        long threadId = Thread.currentThread().getId();
        queue(threadId).addLast(element);
    }

    /** Puts something onto the front of the queue it just came from. */
    synchronized void restore(Map.Entry<Long, T> element) {
        size.incrementAndGet();
        queue(element.getKey()).addFirst(element.getValue());
    }

    /** Grab an entry from a queue. Returns an entry that can be passed back to {@link #restore} if necessary. */
    @Nullable
    Map.Entry<Long, T> poll() {
        if (size.get() > 0) {
            synchronized (this) {
                Map.Entry<Long, Deque<T>> entry = pickNextQueue();
                T result = entry.getValue().poll();
                size.decrementAndGet();

                if (!entry.getValue().isEmpty()) {
                    // put it back in the map so we come round to process the rest of its requests
                    queueByThreadId.put(entry.getKey(), entry.getValue());
                }
                return Maps.immutableEntry(entry.getKey(), result);
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
        return queueByThreadId.computeIfAbsent(threadId, createNewQueue);
    }
}
