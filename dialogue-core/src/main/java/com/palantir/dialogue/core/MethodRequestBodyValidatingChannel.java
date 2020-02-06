package com.palantir.dialogue.core;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;

/** Validates that <code>GET</code> and <code>DELETE</code> requests do not contain bodies. */
final class MethodRequestBodyValidatingChannel implements Channel {

    private final Channel delegate;

    MethodRequestBodyValidatingChannel(Channel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        HttpMethod method = endpoint.httpMethod();
        if ((method == HttpMethod.DELETE || method == HttpMethod.GET)
                && request.body().isPresent()) {
            return Futures.immediateFailedFuture(
                    new SafeIllegalArgumentException("GET and DELETE endpoints must not have a request body"));
        }
        return delegate.execute(endpoint, request);
    }
}
