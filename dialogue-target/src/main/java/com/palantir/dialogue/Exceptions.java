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

import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.RemoteException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Provides utility functions for exception handling. */
public final class Exceptions {

    private Exceptions() {}

    /**
     * Similar to {@link com.google.common.util.concurrent.Futures#getUnchecked(Future)}, except it propagates
     * {@link RemoteException}s directly, rather than wrapping them in an {@link UncheckedExecutionException}.
     */
    public static <T> T getUnchecked(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RemoteException) {
                RemoteException remoteException = (RemoteException) e.getCause();
                RemoteException correctStackTrace = new RemoteException(
                        remoteException.getError(),
                        remoteException.getStatus());
                correctStackTrace.initCause(e);
                throw correctStackTrace;
            }

            return defaultGetUnchecked(e.getCause());
        }
    }

     // Equivalent to the behavior in Futures.getUnchecked(Future)
    private static <T> T defaultGetUnchecked(Throwable exception) {
        if (exception instanceof Error) {
            throw new ExecutionError((Error) exception);
        } else {
            throw new UncheckedExecutionException(exception);
        }
    }
}
