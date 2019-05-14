/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompletableToListenableFutureTest {

    @Mock private Runnable runnable;
    private CompletableFuture<String> originalFuture;
    private CompletableToListenableFuture<String> wrappedFuture;

    @Before
    public void before() {
        originalFuture = new CompletableFuture<>();
        wrappedFuture = new CompletableToListenableFuture<>(originalFuture);
    }
    @Test
    public void testAddListener() {
        wrappedFuture.addListener(runnable, MoreExecutors.directExecutor());

        verify(runnable, never()).run();
        originalFuture.complete("done");
        verify(runnable).run();
    }

    @Test
    public void testExceptions() throws ExecutionException, InterruptedException {
        ListenableFuture<String> listenableFuture = SettableFuture.create();

        listenableFuture.cancel(true);

        listenableFuture.get();
    }

    @Test
    public void testExceptions2() throws ExecutionException, InterruptedException {
        CompletableFuture<String> completableFuture = new CompletableFuture<>();

        completableFuture.cancel(true);

        completableFuture.get();
    }
}
