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

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.DialogueException;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Package private internal API. */
enum DefaultClients implements Clients {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(DefaultClients.class);

    @Override
    public <T> ListenableFuture<T> call(
            Channel channel, Endpoint endpoint, Request request, Deserializer<T> deserializer) {
        // When this method is called, the EndpointChannel can be used at most once. Do not use the bind function
        // because the reloadable state setup cost will never be recovered in this case.
        return call(new EndpointChannelAdapter(endpoint, channel), request, deserializer);
    }

    @Override
    public <T> ListenableFuture<T> call(EndpointChannel channel, Request request, Deserializer<T> deserializer) {
        Optional<String> accepts = deserializer.accepts();
        Request outgoingRequest = accepts.isPresent() ? accepting(request, accepts.get()) : request;
        ListenableFuture<Response> response =
                closeRequestBodyOnCompletion(channel.execute(outgoingRequest), outgoingRequest);
        return Futures.transform(response, deserializer::deserialize, MoreExecutors.directExecutor());
    }

    @Override
    public EndpointChannel bind(Channel channel, Endpoint endpoint) {
        if (channel instanceof EndpointChannelFactory) {
            return ((EndpointChannelFactory) channel).endpoint(endpoint);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Channel of type {} does not implement EndpointChannelFactory, "
                            + "which is recommended for maximum performance. Falling back to lambda impl.",
                    SafeArg.of("type", channel.getClass().getSimpleName()),
                    SafeArg.of("serviceName", endpoint.serviceName()),
                    SafeArg.of("endpointName", endpoint.endpointName()),
                    UnsafeArg.of("channel", channel),
                    new SafeRuntimeException("Exception for stacktrace"));
        }
        return new EndpointChannelAdapter(endpoint, channel);
    }

    private static ListenableFuture<Response> closeRequestBodyOnCompletion(
            ListenableFuture<Response> responseFuture, Request request) {
        Optional<RequestBody> requestBody = request.body();
        if (requestBody.isPresent()) {
            responseFuture.addListener(requestBody.get()::close, MoreExecutors.directExecutor());
        }
        return responseFuture;
    }

    @Override
    public <T> T block(ListenableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!future.cancel(true)) {
                Futures.addCallback(future, CancelListener.INSTANCE, MoreExecutors.directExecutor());
            }
            throw new DialogueException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            // TODO(jellis): can consider propagating other relevant exceptions (eg: HttpConnectTimeoutException)
            // see HttpClientImpl#send(HttpRequest req, BodyHandler<T> responseHandler)
            if (cause instanceof RemoteException) {
                throw newRemoteException((RemoteException) cause);
            }

            if (cause instanceof UnknownRemoteException) {
                throw newUnknownRemoteException((UnknownRemoteException) cause);
            }

            // In this case we provide a suppressed exception to mark the site where the failure was rethrown
            // to avoid losing data while retaining the original failure information.
            if (cause instanceof RuntimeException) {
                cause.addSuppressed(new SafeRuntimeException("Rethrown by dialogue"));
                throw (RuntimeException) cause;
            }

            // This matches the behavior in Futures.getUnchecked(Future)
            if (cause instanceof Error) {
                cause.addSuppressed(new SafeRuntimeException("Rethrown by dialogue"));
                throw (Error) cause;
            }
            throw new DialogueException(cause);
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
        public void onSuccess(Object result) {
            if (result instanceof Closeable) {
                try {
                    ((Closeable) result).close();
                } catch (IOException | RuntimeException e) {
                    log.info(
                            "Failed to close result of {} after the call was canceled",
                            UnsafeArg.of("result", result),
                            e);
                }
            } else if (result instanceof Optional) {
                Optional<?> resultOptional = (Optional<?>) result;
                if (resultOptional.isPresent()) {
                    onSuccess(resultOptional.get());
                }
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            log.info("Canceled call failed", throwable);
        }
    }

    private static Request accepting(Request original, String acceptValue) {
        Preconditions.checkNotNull(acceptValue, "Accept value is required");
        Preconditions.checkState(!acceptValue.isEmpty(), "Accept value must not be empty");
        if (original.headerParams().containsKey(HttpHeaders.ACCEPT)) {
            log.warn(
                    "Request {} already contains an Accept header value {}",
                    UnsafeArg.of("request", original),
                    SafeArg.of("existingAcceptValue", original.headerParams().get(HttpHeaders.ACCEPT)));
            return original;
        }
        return Request.builder()
                .from(original)
                .putHeaderParams(HttpHeaders.ACCEPT, acceptValue)
                .build();
    }

    private static final class EndpointChannelAdapter implements EndpointChannel {
        private final Endpoint endpoint;
        private final Channel channel;

        EndpointChannelAdapter(Endpoint endpoint, Channel channel) {
            this.endpoint = Preconditions.checkNotNull(endpoint, "Endpoint must not be null");
            this.channel = Preconditions.checkNotNull(channel, "Channel must not be null");
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            return channel.execute(endpoint, request);
        }

        @Override
        public String toString() {
            return "EndpointChannelAdapter{endpoint=" + endpoint + ", channel=" + channel + '}';
        }
    }
}
