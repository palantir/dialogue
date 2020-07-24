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

package com.palantir.dialogue.futures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class DialogueFuturesTest {

    @Test
    void testTransform_success() throws Exception {
        SettableFuture<String> original = SettableFuture.create();
        ListenableFuture<String> doubled = DialogueFutures.transform(original, value -> value + value);
        original.set("a");
        assertThat(doubled.get()).isEqualTo("aa");
    }

    @Test
    void testTransform_originalCancel() {
        SettableFuture<String> original = SettableFuture.create();
        ListenableFuture<String> doubled = DialogueFutures.transform(original, value -> value + value);
        assertThat(doubled.cancel(false)).isTrue();
        assertThat(doubled).isCancelled();
        assertThatThrownBy(doubled::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testTransform_resultCancel() {
        SettableFuture<String> original = SettableFuture.create();
        ListenableFuture<String> doubled = DialogueFutures.transform(original, value -> value + value);
        assertThat(doubled.cancel(false)).isTrue();
        assertThat(doubled).isCancelled();
        assertThatThrownBy(doubled::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testTransformAsync_success() throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.set("a");
        assertThat(transformed).isNotDone();
        two.set("b");
        assertThat(transformed).isDone();
        assertThat(transformed.get()).isEqualTo("b");
    }

    @Test
    void testTransformAsync_originalCancel() {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        assertThat(one.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(transformed::get).isInstanceOf(CancellationException.class);
        // The intermediate future is not impacted
        assertThat(two).isNotDone();
    }

    @Test
    void testTransformAsync_intermediateCancel() {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.set("a");
        assertThat(transformed).isNotDone();
        assertThat(two.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(transformed::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testTransformAsync_resultCancel_othersUnset() {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(one).isCancelled();
    }

    @Test
    void testTransformAsync_resultCancel_afterFirstSet() {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.transformAsync(one, _value -> two);
        one.set("a");
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(one).isNotCancelled();
        assertThat(two).isCancelled();
    }

    @Test
    void testTransformAsync_resultCancel_completed() throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.transformAsync(one, _value -> two);
        one.set("a");
        two.set("b");
        assertThat(transformed.cancel(false)).isFalse();
        assertThat(transformed.get()).isEqualTo("b");
    }

    //

    @Test
    void testCatchingAllAsync_success() throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                DialogueFutures.catchingAllAsync(one, _value -> Futures.immediateFuture("failed"));
        assertThat(transformed).isNotDone();
        one.set("a");
        assertThat(transformed).isDone();
        assertThat(transformed.get()).isEqualTo("a");
    }

    @Test
    void testCatchingAllAsync_catch() throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                DialogueFutures.catchingAllAsync(one, value -> Futures.immediateFuture(value.getMessage()));
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        assertThat(transformed).isDone();
        assertThat(transformed.get()).isEqualTo("a");
    }

    @Test
    void testCatchingAllAsync_originalCancel() {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                DialogueFutures.catchingAllAsync(one, value -> Futures.immediateFuture(value.getMessage()));
        assertThat(transformed).isNotDone();
        assertThat(one.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(transformed::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testCatchingAllAsync_intermediateCancel() {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.catchingAllAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        assertThat(transformed).isNotDone();
        assertThat(two.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(transformed::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testCatchingAllAsync_cancel() {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                DialogueFutures.catchingAllAsync(one, value -> Futures.immediateFuture(value.getMessage()));
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(transformed).isDone();
        assertThatThrownBy(transformed::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testCatchingAllAsync_resultCancel_afterFirstSet() {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.catchingAllAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(one).isNotCancelled();
        assertThat(two).isCancelled();
        assertThatThrownBy(transformed::get).isInstanceOf(CancellationException.class);
    }

    @Test
    void testCatchingAllAsync_resultCancel_completed() throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = DialogueFutures.catchingAllAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        two.set("b");
        assertThat(transformed.cancel(false)).isFalse();
        assertThat(transformed.get()).isEqualTo("b");
    }
}
