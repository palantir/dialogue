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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PreferLowestUpstreamUtilization implements Statistics {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUpstreamUtilization.class);

    private final LoadingCache<Upstream, AtomicInteger> active =
            Caffeine.newBuilder().maximumSize(1000).build(upstream -> new AtomicInteger());
    private final Supplier<ImmutableList<Upstream>> upstreams;
    private final Ticker clock;

    public PreferLowestUpstreamUtilization(Supplier<ImmutableList<Upstream>> upstreams, Ticker clock) {
        this.upstreams = upstreams;
        this.clock = clock;
    }

    @Override
    public InFlightStage recordStart(Upstream upstream, Endpoint _endpoint, Request _request) {
        AtomicInteger atomicInteger = active.get(upstream);
        atomicInteger.incrementAndGet();
        return new InFlightStage() {
            @Override
            public void recordComplete(@Nullable Response _response, @Nullable Throwable _throwable) {
                atomicInteger.decrementAndGet();
            }
        };
    }

    public Optional<Upstream> getBest(Endpoint _endpoint) {
        Optional<Upstream> best = upstreams.get().stream()
                .min(Comparator.comparingInt(upstream -> active.get(upstream).get()));
        log.info("time={} best={} active={}", Duration.ofNanos(clock.read()), best.get(), active.asMap());

        // TODO(dfox): tiebreaking currently always picks the first upstream when they have the same utilization
        return best;
    }
}
