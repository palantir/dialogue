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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DialogueFuturesTest {

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransform_success(Transformer transformer) throws Exception {
        SettableFuture<String> original = SettableFuture.create();
        ListenableFuture<String> doubled = transformer.transform(original, value -> value + value);
        original.set("a");
        assertThat(doubled.get()).isEqualTo("aa");
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransform_originalCancel(Transformer transformer) {
        SettableFuture<String> original = SettableFuture.create();
        ListenableFuture<String> doubled = transformer.transform(original, value -> value + value);
        assertThat(original.cancel(false)).isTrue();
        assertThat(doubled).isCancelled();
        assertThatThrownBy(() -> doubled.get()).isInstanceOf(CancellationException.class);
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransform_resultCancel(Transformer transformer) {
        SettableFuture<String> original = SettableFuture.create();
        ListenableFuture<String> doubled = transformer.transform(original, value -> value + value);
        assertThat(doubled.cancel(false)).isTrue();
        assertThat(doubled).isCancelled();
        assertThatThrownBy(() -> doubled.get()).isInstanceOf(CancellationException.class);
        assertThat(original).isCancelled();
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransformAsync_success(Transformer transformer) throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.set("a");
        assertThat(transformed).isNotDone();
        two.set("b");
        assertThat(transformed).isDone();
        assertThat(transformed.get()).isEqualTo("b");
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransformAsync_originalCancel(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        assertThat(one.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(() -> transformed.get()).isInstanceOf(CancellationException.class);
        assertThat(two).as("The intermediate future is not impacted").isNotDone();
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransformAsync_intermediateCancel(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.set("a");
        assertThat(transformed).isNotDone();
        assertThat(two.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(() -> transformed.get()).isInstanceOf(CancellationException.class);
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransformAsync_resultCancel_othersUnset(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.transformAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(one).isCancelled();
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransformAsync_resultCancel_afterFirstSet(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.transformAsync(one, _value -> two);
        one.set("a");
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(one).isNotCancelled();
        assertThat(two).isCancelled();
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testTransformAsync_resultCancel_completed(Transformer transformer) throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.transformAsync(one, _value -> two);
        one.set("a");
        two.set("b");
        assertThat(transformed.cancel(false)).isFalse();
        assertThat(transformed.get()).isEqualTo("b");
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testCatchingAllAsync_success(Transformer transformer) throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                transformer.catchingAllAsync(one, _value -> Futures.immediateFuture("failed"));
        assertThat(transformed).isNotDone();
        one.set("a");
        assertThat(transformed).isDone();
        assertThat(transformed.get()).isEqualTo("a");
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testCatchingAllAsync_catch(Transformer transformer) throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                transformer.catchingAllAsync(one, value -> Futures.immediateFuture(value.getMessage()));
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        assertThat(transformed).isDone();
        assertThat(transformed.get()).isEqualTo("a");
    }

    // Note: this test is not parameterized due to subtle differences in the 'catching' methods.
    // Guava catching Throwable will catch cancellation, which doesn't match the design of
    // catchingAllAsync.
    @Test
    void testCatchingAllAsync_originalCancel() {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                DialogueFutures.catchingAllAsync(one, value -> Futures.immediateFuture(value.getMessage()));
        assertThat(transformed).isNotDone();
        assertThat(one.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(() -> transformed.get()).isInstanceOf(CancellationException.class);
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testCatchingAllAsync_intermediateCancel(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.catchingAllAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        assertThat(transformed).isNotDone();
        assertThat(two.cancel(false)).isTrue();
        assertThat(transformed).isCancelled();
        assertThat(transformed).isDone();
        assertThatThrownBy(() -> transformed.get()).isInstanceOf(CancellationException.class);
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testCatchingAllAsync_cancel(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        ListenableFuture<String> transformed =
                transformer.catchingAllAsync(one, value -> Futures.immediateFuture(value.getMessage()));
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(transformed).isDone();
        assertThatThrownBy(() -> transformed.get()).isInstanceOf(CancellationException.class);
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testCatchingAllAsync_resultCancel_afterFirstSet(Transformer transformer) {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.catchingAllAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        assertThat(transformed).isNotDone();
        assertThat(transformed.cancel(false)).isTrue();
        assertThat(one).isNotCancelled();
        assertThat(two).isCancelled();
        assertThatThrownBy(() -> transformed.get()).isInstanceOf(CancellationException.class);
    }

    @ParameterizedTest
    @EnumSource(Transformer.class)
    void testCatchingAllAsync_resultCancel_completed(Transformer transformer) throws Exception {
        SettableFuture<String> one = SettableFuture.create();
        SettableFuture<String> two = SettableFuture.create();
        ListenableFuture<String> transformed = transformer.catchingAllAsync(one, _value -> two);
        assertThat(transformed).isNotDone();
        one.setException(new RuntimeException("a"));
        two.set("b");
        assertThat(transformed.cancel(false)).isFalse();
        assertThat(transformed.get()).isEqualTo("b");
    }

    @Test
    void testSafeDirectExecutor() {
        AtomicInteger task = new AtomicInteger();
        DialogueFutures.safeDirectExecutor().execute(task::incrementAndGet);
        assertThat(task).hasValue(1);
    }

    @Test
    void testSafeDirectExecutorDoesNotThrowError() {
        Executor executor = DialogueFutures.safeDirectExecutor();
        assertThatCode(() -> executor.execute(() -> {
                    throw new Error();
                }))
                .doesNotThrowAnyException();
    }

    @Test
    void testAddDirectListenerContinuesAfterError() {
        SettableFuture<String> future = SettableFuture.create();
        AtomicInteger invocations = new AtomicInteger();
        Runnable listener = () -> {
            invocations.incrementAndGet();
            throw new Error();
        };
        DialogueFutures.addDirectListener(future, listener);
        DialogueFutures.addDirectListener(future, listener);
        future.set("value");
        assertThat(invocations).hasValue(2);
    }

    @Test
    void testTransformAsyncCancelWhileSettingFuture() throws Exception {
        CountDownLatch transformationEnteredLatch = new CountDownLatch(1);
        CountDownLatch transformationCompletionLatch = new CountDownLatch(1);

        SettableFuture<String> input = SettableFuture.create();
        ListenableFuture<Integer> output = DialogueFutures.transformAsync(input, result -> {
            transformationEnteredLatch.countDown();
            transformationCompletionLatch.await();
            return Futures.immediateFuture(result.length());
        });
        Thread thread = new Thread(() -> input.set(""));
        thread.start();
        transformationEnteredLatch.await();
        assertThat(output.cancel(true)).isFalse();
        assertThat(output.isDone()).isFalse();

        transformationCompletionLatch.countDown();

        assertThat(output.get(1, TimeUnit.SECONDS)).isEqualTo(0);
        thread.join();
    }

    @Test
    void testCatchAllAsyncCancelWhileSettingFuture() throws Exception {
        CountDownLatch transformationEnteredLatch = new CountDownLatch(1);
        CountDownLatch transformationCompletionLatch = new CountDownLatch(1);

        SettableFuture<String> input = SettableFuture.create();
        ListenableFuture<String> output = DialogueFutures.catchingAllAsync(input, _result -> {
            transformationEnteredLatch.countDown();
            transformationCompletionLatch.await();
            return Futures.immediateFuture("value");
        });
        Thread thread = new Thread(() -> input.setException(new RuntimeException()));
        thread.start();
        transformationEnteredLatch.await();
        assertThat(output.cancel(true)).isFalse();
        assertThat(output.isCancelled()).isFalse();
        assertThat(output.isDone()).isFalse();

        transformationCompletionLatch.countDown();

        assertThat(output.get(1, TimeUnit.SECONDS)).isEqualTo("value");
        thread.join();
    }

    public enum Transformer {
        DIALOGUE() {
            @Override
            <I, O> ListenableFuture<O> transform(ListenableFuture<I> input, Function<? super I, ? extends O> function) {
                return DialogueFutures.transform(input, function);
            }

            @Override
            <I, O> ListenableFuture<O> transformAsync(
                    ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function) {
                return DialogueFutures.transformAsync(input, function);
            }

            @Override
            <T> ListenableFuture<T> catchingAllAsync(ListenableFuture<T> input, AsyncFunction<Throwable, T> function) {
                return DialogueFutures.catchingAllAsync(input, function);
            }
        },
        GUAVA() {
            @Override
            <I, O> ListenableFuture<O> transform(ListenableFuture<I> input, Function<? super I, ? extends O> function) {
                return Futures.transform(input, function::apply, DialogueFutures.safeDirectExecutor());
            }

            @Override
            <I, O> ListenableFuture<O> transformAsync(
                    ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function) {
                return Futures.transformAsync(input, function, DialogueFutures.safeDirectExecutor());
            }

            @Override
            <T> ListenableFuture<T> catchingAllAsync(ListenableFuture<T> input, AsyncFunction<Throwable, T> function) {
                return Futures.catchingAsync(input, Throwable.class, function, DialogueFutures.safeDirectExecutor());
            }
        };

        abstract <I, O> ListenableFuture<O> transform(
                ListenableFuture<I> input, Function<? super I, ? extends O> function);

        abstract <I, O> ListenableFuture<O> transformAsync(
                ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function);

        abstract <T> ListenableFuture<T> catchingAllAsync(
                ListenableFuture<T> input, AsyncFunction<Throwable, T> function);
    }
}
