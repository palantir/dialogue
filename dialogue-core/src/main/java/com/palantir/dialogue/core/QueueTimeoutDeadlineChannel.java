/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.deadlines.Deadlines;
import com.palantir.deadlines.Deadlines.Enforcement;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import java.time.Duration;
import java.util.Optional;

/**
 * Resolves the enforced deadline once for an entire request. Queues use the resolved deadline as an upper bound on
 * the configured queue timeout, and reuse it across retries.
 */
final class QueueTimeoutDeadlineChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final Ticker clock;
    private final Enforcement clientEnforcement;

    static QueueTimeoutDeadlineChannel create(Config config, EndpointChannel delegate) {
        return new QueueTimeoutDeadlineChannel(delegate, config.ticker(), config.deadlineEnforcement());
    }

    QueueTimeoutDeadlineChannel(EndpointChannel delegate, Ticker clock, Optional<Boolean> deadlineEnforcement) {
        this.delegate = delegate;
        this.clock = clock;
        this.clientEnforcement = deadlineEnforcement
                .map(value -> value ? Enforcement.ENFORCE : Enforcement.DISABLE)
                .orElse(Enforcement.DEFER);
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        QueueTimeoutAttachments.clearDeadlineExpiration(request);
        enforcedRemainingDeadline()
                .ifPresent(remaining ->
                        QueueTimeoutAttachments.setDeadlineExpiration(request, clock.read() + remaining.toNanos()));

        ListenableFuture<Response> result;
        try {
            result = delegate.execute(request);
        } catch (RuntimeException | Error throwable) {
            QueueTimeoutAttachments.clearDeadlineExpiration(request);
            throw throwable;
        }
        return DialogueFutures.addDirectListener(
                result, () -> QueueTimeoutAttachments.clearDeadlineExpiration(request));
    }

    private Optional<Duration> enforcedRemainingDeadline() {
        Optional<Enforcement> traceEnforcement = Deadlines.getEnforcement();
        if (traceEnforcement.isEmpty()
                || traceEnforcement.get().resolveWith(clientEnforcement) != Enforcement.ENFORCE) {
            return Optional.empty();
        }
        return Deadlines.getRemainingDeadline();
    }
}
