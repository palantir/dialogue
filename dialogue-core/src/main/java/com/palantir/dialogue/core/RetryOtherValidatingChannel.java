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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.RateLimiter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.UnsafeArg;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RetryOtherValidatingChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(RetryOtherValidatingChannel.class);
    private static final RateLimiter UNKNOWN_RETRY_OTHER_URI = RateLimiter.create(1);

    private final Channel delegate;
    private final List<String> uris;
    private final UnsafeArg<List<String>> unsafeArg;

    private RetryOtherValidatingChannel(Channel delegate, List<String> uris) {
        this.delegate = delegate;
        this.uris = uris;
        this.unsafeArg = UnsafeArg.of("uris", uris);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return DialogueFutures.transform(delegate.execute(endpoint, request), response -> {
            Optional<String> maybeRetryOtherHost = response.getFirstHeader(HttpHeaders.LOCATION);
            if (maybeRetryOtherHost.isPresent()) {
                String retryOtherHost = maybeRetryOtherHost.get();
                if (!isKnown(maybeRetryOtherHost.get()) && UNKNOWN_RETRY_OTHER_URI.tryAcquire()) {
                    log.info("Unknown Location header", UnsafeArg.of("location", retryOtherHost), unsafeArg);
                }
            }

            return response;
        });
    }

    private boolean isKnown(String retryOtherUri) {
        for (int i = 0; i < uris.size(); i++) {
            if (uris.get(i).equals(retryOtherUri)) {
                return true;
            }
        }
        return false;
    }

    static RetryOtherValidatingChannel create(Config cf, Channel delegate) {
        return new RetryOtherValidatingChannel(delegate, cf.clientConf().uris());
    }
}
