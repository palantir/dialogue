/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.RateLimiter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;

final class RetryOtherValidatingChannel implements Channel {

    private static final SafeLogger log = SafeLoggerFactory.get(RetryOtherValidatingChannel.class);
    private static final RateLimiter VALIDATION_FAILED_LOGGING_LIMITER = RateLimiter.create(1, Duration.ZERO);

    private final Channel delegate;
    private final Set<String> hosts;
    private final FutureCallback<Response> callback;
    private final Consumer<String> failureReporter;

    RetryOtherValidatingChannel(Channel delegate, Set<String> hosts) {
        this(delegate, hosts, RetryOtherValidatingChannel.failureReporter(hosts));
    }

    @VisibleForTesting
    RetryOtherValidatingChannel(Channel delegate, Set<String> hosts, Consumer<String> failureReporter) {
        this.delegate = delegate;
        this.hosts = hosts;
        callback = new FutureCallback<>() {
            @Override
            public void onSuccess(Response result) {
                validateRetryOther(result);
            }

            @Override
            public void onFailure(Throwable _throwable) {}
        };
        this.failureReporter = failureReporter;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return DialogueFutures.addDirectCallback(delegate.execute(endpoint, request), callback);
    }

    private void validateRetryOther(Response response) {
        if (!Responses.isRetryOther(response)) {
            return;
        }
        Optional<String> maybeRetryOtherUri = response.getFirstHeader(HttpHeaders.LOCATION);
        if (maybeRetryOtherUri.isPresent()) {
            String retryOtherUri = maybeRetryOtherUri.get();
            if (!isValidUri(retryOtherUri)) {
                failureReporter.accept(retryOtherUri);
            }
        }
    }

    private boolean isValidUri(String uri) {
        String maybeHost = maybeParseHost(uri);
        return (maybeHost != null) && hosts.contains(maybeHost);
    }

    static Channel create(Config cf, Channel delegate) {
        try {
            Set<String> hosts = cf.clientConf().uris().stream()
                    .map(RetryOtherValidatingChannel::strictParseHost)
                    .collect(ImmutableSet.toImmutableSet());
            return new RetryOtherValidatingChannel(delegate, hosts);
        } catch (RuntimeException e) {
            log.warn("Could not parse uris, turning off Location header validation", e);
            return delegate;
        }
    }

    @VisibleForTesting
    static String strictParseHost(String uri) {
        String maybeHost = maybeParseHost(uri);
        if (maybeHost != null) {
            return maybeHost;
        }
        throw new SafeIllegalArgumentException("Failed to parse URI", UnsafeArg.of("uri", uri));
    }

    @CheckForNull
    private static String maybeParseHost(String uri) {
        try {
            URL parsed = new URL(uri);
            return parsed.getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static Consumer<String> failureReporter(Set<String> hosts) {
        UnsafeArg<Set<String>> unsafeUris = UnsafeArg.of("uris", hosts);
        return retryOtherUri -> {
            if (VALIDATION_FAILED_LOGGING_LIMITER.tryAcquire()) {
                log.info("Invalid Location header value {} {}", UnsafeArg.of("location", retryOtherUri), unsafeUris);
            }
        };
    }
}
