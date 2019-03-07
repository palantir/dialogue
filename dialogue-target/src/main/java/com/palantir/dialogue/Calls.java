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

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.errors.RemoteException;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

public final class Calls {
    private Calls() {}

    /**
     * Converts the given {@link Call} to a {@link Future} by {@link Call#execute executing} the Call and capturing its
     * result. If the call fails with an {@link Observer#exception} or {@link Observer#failure}, the
     * corresponding exception is captured by the Future and will be rethrown by {@link Future#get}. Cancelling the
     * future cancels the underlying call.
     */
    public static ListenableFuture<Response> toFuture(Call call) {
        ExceptionAwareFuture<Response> future = new ExceptionAwareFuture<>(call);
        call.execute(new Observer() {
            @Override
            public void success(Response value) {
                future.set(value);
            }

            @Override
            public void failure(RemoteException error) {
                future.setException(error);
            }

            @Override
            public void exception(Throwable throwable) {
                future.setException(throwable);
            }
        });
        return future;
    }

    private static final class ExceptionAwareFuture<RespT> extends AbstractFuture<RespT> {
        private final Call call;

        private ExceptionAwareFuture(Call call) {
            this.call = call;
        }

        @Override
        protected void interruptTask() {
            super.interruptTask();
            call.cancel();
        }

        @Override
        protected boolean set(@Nullable RespT value) {
            return super.set(value);
        }

        @Override
        protected boolean setException(Throwable throwable) {
            return super.setException(throwable);
        }
    }
}
