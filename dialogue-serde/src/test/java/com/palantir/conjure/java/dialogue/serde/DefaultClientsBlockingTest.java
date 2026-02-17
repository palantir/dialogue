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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.api.errors.AbstractSerializableError;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.SerializableErrorProvider;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.dialogue.DialogueException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
                .hasMessage("Response status: 502")
                .satisfies(exception -> {
                    assertThat(((UnknownRemoteException) exception).getBody()).isEqualTo("Nginx broke");
                });
    }

    @Test
    public void testQosExceptionThrottle() {
        QosException qosException = QosException.throttle(QosReason.of("test-reason"));
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(qosException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(QosException.Throttle.class)
                .isNotSameAs(qosException)
                .hasCause(qosException)
                .satisfies(exception ->
                        assertThat(((QosException) exception).getReason()).isEqualTo(QosReason.of("test-reason")));
    }

    @Test
    public void testQosExceptionThrottleWithRetryAfter() {
        QosException qosException = QosException.throttle(QosReason.of("test-reason"), Duration.ofSeconds(30));
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(qosException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(QosException.Throttle.class)
                .isNotSameAs(qosException)
                .hasCause(qosException)
                .satisfies(exception -> {
                    QosException.Throttle throttle = (QosException.Throttle) exception;
                    assertThat(throttle.getReason()).isEqualTo(QosReason.of("test-reason"));
                    assertThat(throttle.getRetryAfter()).hasValue(Duration.ofSeconds(30));
                });
    }

    @Test
    public void testQosExceptionUnavailable() {
        QosException qosException = QosException.unavailable(QosReason.of("test-unavailable"));
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(qosException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(QosException.Unavailable.class)
                .isNotSameAs(qosException)
                .hasCause(qosException)
                .satisfies(exception ->
                        assertThat(((QosException) exception).getReason()).isEqualTo(QosReason.of("test-unavailable")));
    }

    @Test
    public void testQosExceptionStackTraceIncludesBlockCallSite() {
        QosException qosException = QosException.throttle(QosReason.of("test-reason"));
        // Clear the stack trace to simulate an exception created on a remote/async thread
        qosException.setStackTrace(new StackTraceElement[0]);
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(qosException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(QosException.Throttle.class)
                .satisfies(exception -> assertThat(exception.getStackTrace())
                        .extracting(StackTraceElement::getMethodName)
                        .contains("testQosExceptionStackTraceIncludesBlockCallSite"));
    }

    @Test
    public void testRuntimeException() {
        RuntimeException runtimeException = new RuntimeException();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(runtimeException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isSameAs(runtimeException)
                .satisfies(exception -> assertThat(exception.getSuppressed()).hasSize(1))
                .satisfies(exception -> assertThat(exception.getSuppressed()[0])
                        .isInstanceOf(SafeRuntimeException.class)
                        .hasMessage("Rethrown by dialogue"));
    }

    @Test
    public void testException() {
        Exception exception = new Exception();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(exception);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture))
                .isInstanceOf(DialogueException.class)
                .hasCause(exception);
    }

    @Test
    public void testError() {
        Error error = new Error();
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(error);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture)).isSameAs(error);
    }

    @Test
    public void testInterruption() {
        ListenableFuture<Object> future = SettableFuture.create();
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(future))
                .isInstanceOf(DialogueException.class)
                .hasCauseInstanceOf(InterruptedException.class);
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
                .isInstanceOf(DialogueException.class)
                .hasCauseInstanceOf(InterruptedException.class);
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
                .isInstanceOf(DialogueException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        // Clear interrupted state as well as test.
        assertThat(Thread.interrupted())
                .as("getUnchecked should not clear interrupted state")
                .isTrue();
        verify(responseBody).close();
    }

    @Test
    public void testUserDefinedRemoteExceptionSubclass() {
        CustomRemoteException customException = new CustomRemoteException(
                new MySerializableError(
                        ErrorType.INVALID_ARGUMENT.code().toString(),
                        ErrorType.INVALID_ARGUMENT.name(),
                        "instanceId",
                        new MyParams("myFieldValue")),
                ErrorType.INVALID_ARGUMENT.httpErrorCode());
        ListenableFuture<Object> failedFuture = Futures.immediateFailedFuture(customException);

        assertThatThrownBy(() -> DefaultClients.INSTANCE.block(failedFuture)).satisfies(exception -> {
            assertThat(exception).isInstanceOf(CustomRemoteException.class);
            assertThat(exception).hasMessageContainingAll("Default:InvalidArgument", "{someField=myFieldValue}");
            assertThat(exception).isInstanceOf(RemoteException.class);
            assertThat(exception.getCause()).isInstanceOfSatisfying(CustomRemoteException.class, cause -> {
                assertThat(cause).isNotNull();
                assertThat(cause).hasMessageContainingAll("Default:InvalidArgument", "{someField=myFieldValue}");
            });
        });
    }

    public record MyParams(String someField) {}

    public static final class MySerializableError extends AbstractSerializableError<MyParams> {
        public MySerializableError(String errorCode, String errorName, String errorInstanceId, MyParams parameters) {
            super(errorCode, errorName, errorInstanceId, parameters);
        }

        SerializableError toSerializableError() {
            return SerializableError.builder()
                    .errorCode(errorCode())
                    .errorName(errorName())
                    .errorInstanceId(errorInstanceId())
                    .parameters(Map.of("someField", parameters().someField()))
                    .build();
        }
    }

    public static final class CustomRemoteException extends RemoteException
            implements SerializableErrorProvider<MyParams> {
        private final MySerializableError error;

        public CustomRemoteException(MySerializableError error, int status) {
            super(error.toSerializableError(), status);
            this.error = error;
        }

        @Override
        public MySerializableError error() {
            return this.error;
        }
    }

    private static RemoteException remoteException(ServiceException exception) {
        return new RemoteException(
                SerializableError.forException(exception),
                exception.getErrorType().httpErrorCode());
    }
}
