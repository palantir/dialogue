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

package com.palantir.dialogue.core;

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.immutables.value.Value;

/**
 * A channel that logs warnings when the response from a server contains the "deprecation" header. Logs include the
 * content of the "server" header when it is provided, and always include endpoint details.
 *
 * <p>Deprecation warnings are produced at most once per minute per service. The {@code client.deprecations} meter may
 * be used to understand more granular rates of deprecated calls against a particular service using the
 * {@code service-name} tag.
 */
final class DeprecationWarningChannel implements EndpointChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(DeprecationWarningChannel.class);
    // Static cache avoids poor interactions with the jaxrs shim and consumers which recreate clients too aggressively.
    private static final Cache<LoggingRateLimiterKey, Object> loggingRateLimiter =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build();
    private static final Object SENTINEL = new Object();

    private final EndpointChannel delegate;
    private final ClientMetrics metrics;
    private final FutureCallback<Response> callback;

    private DeprecationWarningChannel(
            EndpointChannel delegate, TaggedMetricRegistry metrics, Endpoint endpoint, String channelName) {
        this.delegate = delegate;
        this.metrics = ClientMetrics.of(metrics);
        this.callback = createCallback(channelName, endpoint);
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        TaggedMetricRegistry metrics = cf.clientConf().taggedMetricRegistry();
        return new DeprecationWarningChannel(delegate, metrics, endpoint, cf.channelName());
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        ListenableFuture<Response> future = delegate.execute(request);
        DialogueFutures.addDirectCallback(future, callback);
        return future;
    }

    private FutureCallback<Response> createCallback(String channelName, Endpoint endpoint) {
        // lazily create meter metric name only if deprecated endpoint is accessed
        Supplier<Meter> meterSupplier = Suppliers.memoize(() -> metrics.deprecations(endpoint.serviceName()));
        return DialogueFutures.onSuccess(response -> {
            if (response == null) {
                return;
            }

            Optional<String> maybeHeader = response.getFirstHeader("deprecation");
            if (maybeHeader.isEmpty()) {
                return;
            }

            meterSupplier.get().mark();
            if (log.isWarnEnabled() && tryAcquire(channelName, endpoint)) {
                log.warn(
                        "Using a deprecated endpoint when connecting to service",
                        SafeArg.of("channelName", channelName),
                        SafeArg.of("serviceName", endpoint.serviceName()),
                        SafeArg.of("endpointHttpMethod", endpoint.httpMethod()),
                        SafeArg.of("endpointName", endpoint.endpointName()),
                        SafeArg.of("endpointClientVersion", endpoint.version()),
                        SafeArg.of("server", response.getFirstHeader("server").orElse("no server header provided")));
            }
        });
    }

    /**
     * Returns true when the loggingRateLimiter permits logging for the provided serviceName, and false otherwise.
     *
     * <p>Note: this method is not synchronized because the throttling is best effort rather than guaranteed -- the
     * penalty for failing to rate limit correctly is a few extra log lines, and we choose that penalty over the cost
     * of synchronization.
     */
    private boolean tryAcquire(String channelName, Endpoint endpoint) {
        LoggingRateLimiterKey key =
                LoggingRateLimiterKey.of(channelName, endpoint.serviceName(), endpoint.endpointName());
        if (loggingRateLimiter.getIfPresent(key) == null) {
            loggingRateLimiter.put(key, SENTINEL);
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "DeprecationWarningChannel{" + delegate + '}';
    }

    @Value.Immutable
    interface LoggingRateLimiterKey {
        @Value.Parameter
        String channelName();

        @Value.Parameter
        String serviceName();

        @Value.Parameter
        String endpointName();

        static LoggingRateLimiterKey of(String channel, String service, String endpoint) {
            return ImmutableLoggingRateLimiterKey.of(channel, service, endpoint);
        }
    }
}
