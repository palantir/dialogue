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

import com.codahale.metrics.Meter;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Cause;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Level;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Result;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLException;
import org.jspecify.annotations.Nullable;

/**
 * Records the {@code dialogue.concurrencylimiter.decision} meter, describing how a concurrency limiter interpreted
 * each completed request. Decisions are recorded for every request, so meters are resolved lazily and cached.
 */
final class ConcurrencyLimiterDecisionRecorder {

    static final ConcurrencyLimiterDecisionRecorder NOOP =
            new ConcurrencyLimiterDecisionRecorder(null, "noop", Decision_Level.HOST);

    // Failures are often wrapped by the client implementation, so a few levels of causes are inspected.
    private static final int MAX_CAUSE_DEPTH = 3;

    @Nullable
    private final DialogueConcurrencylimiterMetrics metrics;

    private final String channelName;
    private final Decision_Level level;

    // Only populated with the causes that occur, so unused combinations are never registered or reported.
    private final ConcurrentMap<Decision_Cause, Meter> successMeters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Decision_Cause, Meter> droppedMeters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Decision_Cause, Meter> ignoredMeters = new ConcurrentHashMap<>();

    private ConcurrencyLimiterDecisionRecorder(
            @Nullable DialogueConcurrencylimiterMetrics metrics, String channelName, Decision_Level level) {
        this.metrics = metrics;
        this.channelName = channelName;
        this.level = level;
    }

    static ConcurrencyLimiterDecisionRecorder create(
            TaggedMetricRegistry registry, String channelName, Decision_Level level) {
        return new ConcurrencyLimiterDecisionRecorder(
                DialogueConcurrencylimiterMetrics.of(registry), channelName, level);
    }

    void record(Decision_Result result, Decision_Cause cause) {
        if (metrics == null) {
            return;
        }
        ConcurrentMap<Decision_Cause, Meter> byCause = switch (result) {
            case SUCCESS -> successMeters;
            case DROPPED -> droppedMeters;
            case IGNORED -> ignoredMeters;
        };
        Meter meter = byCause.get(cause);
        if (meter == null) {
            // Racing threads resolve the same meter from the registry, so the last write wins harmlessly.
            meter = metrics.decision()
                    .channelName(channelName)
                    .level(level)
                    .result(result)
                    .cause(cause)
                    .build();
            byCause.put(cause, meter);
        }
        meter.mark();
    }

    static Decision_Cause causeOf(Response response) {
        if (Responses.isQosDueToCustom(response)) {
            return Decision_Cause.QOS_CUSTOM;
        }
        if (Responses.isRetryOther(response)) {
            return Decision_Cause.HTTP_308;
        }
        return switch (response.code()) {
            case 429 -> Decision_Cause.HTTP_429;
            case 500 -> Decision_Cause.HTTP_500;
            case 503 -> Decision_Cause.HTTP_503;
            default ->
                switch (response.code() / 100) {
                    case 2 -> Decision_Cause.HTTP_2XX;
                    case 3 -> Decision_Cause.HTTP_3XX;
                    case 4 -> Decision_Cause.HTTP_4XX;
                    case 5 -> Decision_Cause.HTTP_5XX;
                    default -> Decision_Cause.OTHER;
                };
        };
    }

    static Decision_Cause causeOf(Throwable throwable) {
        if (throwable instanceof CancellationException) {
            return Decision_Cause.CANCELLED;
        }
        if (!(throwable instanceof IOException)) {
            return Decision_Cause.OTHER;
        }
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            Decision_Cause cause = ioCauseOf(current);
            if (cause != null) {
                return cause;
            }
            current = current.getCause();
        }
        return Decision_Cause.IO_ERROR;
    }

    @Nullable
    private static Decision_Cause ioCauseOf(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return Decision_Cause.SOCKET_TIMEOUT;
        }
        // dialogue-core cannot reference client implementation types, so connect timeouts are matched by name. The
        // Apache client wraps org.apache.hc.client5.http.ConnectTimeoutException in a SafeConnectTimeoutException.
        if (throwable.getClass().getSimpleName().endsWith("ConnectTimeoutException")) {
            return Decision_Cause.CONNECT_TIMEOUT;
        }
        if (throwable instanceof ConnectException || throwable instanceof NoRouteToHostException) {
            return Decision_Cause.CONNECT_ERROR;
        }
        if (throwable instanceof UnknownHostException) {
            return Decision_Cause.UNKNOWN_HOST;
        }
        if (throwable instanceof SSLException) {
            return Decision_Cause.SSL;
        }
        if (throwable instanceof SocketException) {
            return Decision_Cause.SOCKET_ERROR;
        }
        return null;
    }
}
