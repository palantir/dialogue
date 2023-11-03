/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.core;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * This channel validates early in the process that request body implementations
 * do not violate the {@link RequestBody} contract.
 */
final class RequestBodyValidationChannel implements EndpointChannel {

    private static final Consumer<RequestBody> BODY_VALIDATOR = RequestBodyValidationChannel::validate;
    private final EndpointChannel delegate;

    RequestBodyValidationChannel(EndpointChannel delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate is required");
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        validate(request);
        return delegate.execute(request);
    }

    private static void validate(Request request) {
        Optional<RequestBody> body = request.body();
        body.ifPresent(BODY_VALIDATOR);
    }

    private static void validate(RequestBody body) {
        Preconditions.checkNotNull(body.contentType(), "RequestBody.contentType is required");
    }
}
