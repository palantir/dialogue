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

/**
 * Modified from upstream.
 *
 * Copyright (c) 2000-2017, jMock.org
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. Redistributions
 * in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * Neither the name of jMock nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
@SuppressWarnings({"all", "PreferSafeLoggableExceptions"})
public final class DeltaQueue<T> {
    private static class Node<T> {
        public final T value;
        public long delay;
        public DeltaQueue.Node<T> next = null;

        public Node(T value, long nanos) {
            this.value = value;
            this.delay = nanos;
        }
    }

    private DeltaQueue.Node<T> head = null;

    public synchronized boolean isEmpty() {
        return head == null;
    }

    public synchronized boolean isNotEmpty() {
        return !isEmpty();
    }

    public synchronized T next() {
        return head.value;
    }

    public synchronized long delay() {
        return head.delay;
    }

    public synchronized long delay(T element) {
        long ret = 0;
        DeltaQueue.Node<T> next = head;
        while (next != null) {
            ret += next.delay;
            if (next.value.equals(element)) {
                break;
            }
            next = next.next;
        }
        if (next == null) {
            return -1;
        }
        return ret;
    }

    public synchronized void add(long delay, T value) {
        DeltaQueue.Node<T> newNode = new DeltaQueue.Node<T>(value, delay);

        DeltaQueue.Node<T> prev = null;
        DeltaQueue.Node<T> next = head;

        while (next != null && next.delay <= newNode.delay) {
            newNode.delay -= next.delay;
            prev = next;
            next = next.next;
        }

        if (prev == null) {
            head = newNode;
        } else {
            prev.next = newNode;
        }

        if (next != null) {
            next.delay -= newNode.delay;

            newNode.next = next;
        }
    }

    public synchronized long tick(long timeUnits) {
        if (head == null) {
            return 0L;
        } else if (head.delay >= timeUnits) {
            head.delay -= timeUnits;
            return 0L;
        } else {
            long leftover = timeUnits - head.delay;
            head.delay = 0L;
            return leftover;
        }
    }

    public synchronized T pop() {
        if (head.delay > 0) {
            throw new IllegalStateException("cannot pop the head element when it has a non-zero delay");
        }

        T popped = head.value;
        head = head.next;
        return popped;
    }

    public synchronized boolean remove(T element) {
        DeltaQueue.Node<T> prev = null;
        DeltaQueue.Node<T> node = head;
        while (node != null && node.value != element) {
            prev = node;
            node = node.next;
        }

        if (node == null) {
            return false;
        }

        if (node.next != null) {
            node.next.delay += node.delay;
        }

        if (prev == null) {
            head = node.next;
        } else {
            prev.next = node.next;
        }

        return true;
    }

    // Added methods
    public synchronized T maybePop() {
        if (head.delay > 0) {
            return null;
        }

        T popped = head.value;
        head = head.next;
        return popped;
    }

    public synchronized boolean isIdle() {
        return isEmpty() || delay() > 0;
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append("[");

        DeltaQueue.Node<T> node = head;
        while (node != null) {
            if (node != head) {
                sb.append(", ");
            }
            sb.append("+").append(node.delay).append(": ").append(node.value);

            node = node.next;
        }
        sb.append("]");

        return sb.toString();
    }
}
