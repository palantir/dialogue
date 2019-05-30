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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import org.junit.Test;

public class ExceptionsTest {

    @Test
    public void testSuccess() {
        ListenableFuture<String> future = Futures.immediateFuture("success");

        assertThat(Exceptions.getUnchecked(future)).isEqualTo("success");
    }

    @Test
    public void testRemoteException() {
        RemoteException remoteException = remoteException(new ServiceException(ErrorType.INVALID_ARGUMENT));
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(remoteException);

        assertThatThrownBy(() -> Exceptions.getUnchecked(failedFuture))
                .isInstanceOf(RemoteException.class)
                .hasFieldOrPropertyWithValue("status", ErrorType.INVALID_ARGUMENT.httpErrorCode());
    }

    @Test
    public void testRuntimeException() {
        RuntimeException runtimeException = new RuntimeException();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(runtimeException);

        assertThatThrownBy(() -> Exceptions.getUnchecked(failedFuture))
                .isInstanceOf(UncheckedExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    public void testException() {
        Exception exception = new Exception();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(exception);

        assertThatThrownBy(() -> Exceptions.getUnchecked(failedFuture))
                .isInstanceOf(UncheckedExecutionException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    public void testError() {
        Error error = new Error();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(error);

        assertThatThrownBy(() -> Exceptions.getUnchecked(failedFuture))
                .isInstanceOf(ExecutionError.class)
                .hasCauseInstanceOf(Error.class);
    }

    private static RemoteException remoteException(ServiceException exception) {
        return new RemoteException(
                SerializableError.forException(exception),
                exception.getErrorType().httpErrorCode());
    }
}