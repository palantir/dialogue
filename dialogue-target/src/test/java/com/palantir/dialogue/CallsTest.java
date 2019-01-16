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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CallsTest {

    @Mock
    private Call<String> call;

    @Test
    public void toFuture_cancelsCallWhenCancellingWithInterrupt() {
        ListenableFuture<String> future = Calls.toFuture(call);
        future.cancel(true);
        verify(call).cancel();
    }

    @Test
    public void toFuture_doesNotCancelCallWhenNotAllowedToInterrupt() {
        ListenableFuture<String> future = Calls.toFuture(call);
        future.cancel(false);
        verify(call, never()).cancel();
    }
}
