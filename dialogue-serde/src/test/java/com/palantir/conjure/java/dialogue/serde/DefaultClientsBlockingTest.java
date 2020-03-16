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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class DefaultClientsBlockingTest {

    @Test
    public void testSuccess() {
        ListenableFuture<String> future = Futures.immediateFuture("success");

        Assertions.assertThat(DefaultClients.INSTANCE.block(future)).isEqualTo("success");
    }

    @Test
    public void testRemoteException() {
        RemoteException remoteException = remoteException(new ServiceException(ErrorType.INVALID_ARGUMENT));
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(remoteException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(RemoteException.class)
                .hasFieldOrPropertyWithValue("status", ErrorType.INVALID_ARGUMENT.httpErrorCode());
    }

    @Test
    public void testUnknownRemoteException() {
        UnknownRemoteException remoteException = new UnknownRemoteException(502, "Nginx broke");
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(remoteException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(UnknownRemoteException.class)
                .hasMessage("Error 502. (Failed to parse response body as SerializableError.)")
                .satisfies(exception -> {
                    assertThat(((UnknownRemoteException) exception).getBody()).isEqualTo("Nginx broke");
                });
    }

    @Test
    public void testRuntimeException() {
        RuntimeException runtimeException = new RuntimeException();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(runtimeException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(UncheckedExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    public void testException() {
        Exception exception = new Exception();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(exception);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(UncheckedExecutionException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    public void testError() {
        Error error = new Error();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(error);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(ExecutionError.class)
                .hasCauseInstanceOf(Error.class);
    }

    @Test
    public void testInterruption() {
        ListenableFuture<Object> future = SettableFuture.create();
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(future))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessage("Interrupted waiting for future");
        // Clear interrupted state as well as test.
        assertThat(Thread.interrupted())
                .as("getUnchecked should not clear interrupted state")
                .isTrue();
        assertThat(future).isCancelled();
    }

    @Test
    public void testInterruption_resultIsClosed() throws IOException {
        SettableFuture<Object> future = SettableFuture.create();
        InputStream responseBody = mock(InputStream.class);
        future.set(responseBody);
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(future))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessage("Interrupted waiting for future");
        // Clear interrupted state as well as test.
        assertThat(Thread.interrupted())
                .as("getUnchecked should not clear interrupted state")
                .isTrue();
        verify(responseBody).close();
    }

    @Test
    public void testInterruption_optional_resultIsClosed() throws IOException {
        SettableFuture<Object> future = SettableFuture.create();
        InputStream responseBody = mock(InputStream.class);
        future.set(Optional.of(responseBody));
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(future))
                .isInstanceOf(SafeRuntimeException.class)
                .hasMessage("Interrupted waiting for future");
        // Clear interrupted state as well as test.
        assertThat(Thread.interrupted())
                .as("getUnchecked should not clear interrupted state")
                .isTrue();
        verify(responseBody).close();
    }

    private static RemoteException remoteException(ServiceException exception) {
        return new RemoteException(
                SerializableError.forException(exception),
                exception.getErrorType().httpErrorCode());
    }
}
