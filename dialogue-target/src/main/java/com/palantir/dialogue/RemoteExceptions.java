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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides utility functions for exception handling. */
public final class RemoteExceptions {
    private static final Logger log = LoggerFactory.getLogger(RemoteExceptions.class);

    private RemoteExceptions() {}

    /**
     * Similar to {@link com.google.common.util.concurrent.Futures#getUnchecked(Future)}, except it propagates
     * {@link RemoteException}s directly, rather than wrapping them in an {@link UncheckedExecutionException}.
     */
    @SuppressWarnings("deprecation") // match behavior of Futures.getUnchecked(Future)
    public static <T> T getUnchecked(ListenableFuture<T> future) {
        return getUnchecked((Future<T>) future);
    }

    /**
     * Similar to {@link com.google.common.util.concurrent.Futures#getUnchecked(Future)}, except it propagates
     * {@link RemoteException}s directly, rather than wrapping them in an {@link UncheckedExecutionException}.
     *
     * @deprecated Prefer {@link #getUnchecked(ListenableFuture)}
     */
    @Deprecated
    @SuppressWarnings("ThrowError") // match behavior of Futures.getUnchecked(Future)
    public static <T> T getUnchecked(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!future.cancel(true)) {
                if (future instanceof ListenableFuture) {
                    ListenableFuture<T> listenable = (ListenableFuture<T>) future;
                    Futures.addCallback(listenable, CancelListener.INSTANCE, MoreExecutors.directExecutor());
                } else {
                    log.warn("Unable to ensure result of non-listenable future is closed", e);
                }
            }
            throw new SafeRuntimeException("Interrupted waiting for future", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String message = cause.getMessage();

            // TODO(jellis): can consider propagating other relevant exceptions (eg: HttpConnectTimeoutException)
            // see HttpClientImpl#send(HttpRequest req, BodyHandler<T> responseHandler)
            if (cause instanceof RemoteException) {
                throw newRemoteException((RemoteException) cause);
            }

            if (cause instanceof UnknownRemoteException) {
                throw newUnknownRemoteException((UnknownRemoteException) cause);
            }

            // This matches the behavior in Futures.getUnchecked(Future)
            if (cause instanceof Error) {
                throw new ExecutionError(message, (Error) cause);
            } else {
                throw new UncheckedExecutionException(message, cause);
            }
        }
    }

    // Need to create a new exception so our current stacktrace is included in the exception
    private static RemoteException newRemoteException(RemoteException remoteException) {
        RemoteException newException = new RemoteException(remoteException.getError(), remoteException.getStatus());
        newException.initCause(remoteException);
        return newException;
    }

    private static UnknownRemoteException newUnknownRemoteException(UnknownRemoteException cause) {
        UnknownRemoteException newException = new UnknownRemoteException(cause.getStatus(), cause.getBody());
        newException.initCause(cause);
        return newException;
    }

    enum CancelListener implements FutureCallback<Object> {
        INSTANCE;

        @Override
        public void onSuccess(@Nullable Object result) {
            if (result instanceof Closeable) {
                try {
                    ((Closeable) result).close();
                } catch (IOException | RuntimeException e) {
                    log.info(
                            "Failed to close result of {} after the call was canceled",
                            UnsafeArg.of("result", result),
                            e);
                }
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            log.info("Canceled call failed", throwable);
        }
    }
}
