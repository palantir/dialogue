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

package com.palantir.dialogue.hc5;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.Tracer;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/** A simple wrapper around a {@link PoolingHttpClientConnectionManager} which provides instrumentation. */
final class InstrumentedPoolingHttpClientConnectionManager
        implements HttpClientConnectionManager, ConnPoolControl<HttpRoute> {

    private final PoolingHttpClientConnectionManager manager;
    private final Timer connectTimer;

    InstrumentedPoolingHttpClientConnectionManager(
            PoolingHttpClientConnectionManager manager,
            TaggedMetricRegistry registry,
            String clientName,
            String clientType) {
        this.manager = manager;
        this.connectTimer = DialogueClientMetrics.of(registry)
                .connectionCreate()
                .clientName(clientName)
                .clientType(clientType)
                .build();
    }

    @Override
    public void close() {
        manager.close();
    }

    @Override
    public void close(CloseMode closeMode) {
        manager.close(closeMode);
    }

    @Override
    public LeaseRequest lease(String id, HttpRoute route, Timeout requestTimeout, Object state) {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.lease")) {
            return TracedLeaseRequest.wrap(manager.lease(id, route, requestTimeout, state));
        }
    }

    @Override
    public void release(ConnectionEndpoint endpoint, Object state, TimeValue keepAlive) {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.release")) {
            manager.release(endpoint, state, keepAlive);
        }
    }

    @Override
    public void connect(ConnectionEndpoint endpoint, TimeValue connectTimeout, HttpContext context) throws IOException {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.connect");
                Context _timer = connectTimer.time()) {
            manager.connect(endpoint, connectTimeout, context);
        }
    }

    @Override
    public void upgrade(ConnectionEndpoint endpoint, HttpContext context) throws IOException {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.upgrade")) {
            manager.upgrade(endpoint, context);
        }
    }

    @Override
    public void closeIdle(TimeValue idleTime) {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.closeIdle")) {
            manager.closeIdle(idleTime);
        }
    }

    @Override
    public void closeExpired() {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.closeExpired")) {
            manager.closeExpired();
        }
    }

    @Override
    public Set<HttpRoute> getRoutes() {
        return manager.getRoutes();
    }

    @Override
    public int getMaxTotal() {
        return manager.getMaxTotal();
    }

    @Override
    public void setMaxTotal(int max) {
        manager.setMaxTotal(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return manager.getDefaultMaxPerRoute();
    }

    @Override
    public void setDefaultMaxPerRoute(int max) {
        manager.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getMaxPerRoute(HttpRoute route) {
        return manager.getMaxPerRoute(route);
    }

    @Override
    public void setMaxPerRoute(HttpRoute route, int max) {
        manager.setMaxPerRoute(route, max);
    }

    @Override
    public PoolStats getTotalStats() {
        return manager.getTotalStats();
    }

    @Override
    public PoolStats getStats(HttpRoute route) {
        return manager.getStats(route);
    }

    @Override
    public String toString() {
        return "TracedPoolingHttpClientConnectionManager{" + manager + '}';
    }

    private static final class TracedLeaseRequest implements LeaseRequest {

        private final LeaseRequest delegate;
        private final DetachedSpan span;

        static LeaseRequest wrap(LeaseRequest request) {
            if (Tracer.hasTraceId() && Tracer.isTraceObservable()) {
                return new TracedLeaseRequest(request, DetachedSpan.start("Dialogue ConnectionManager LeaseRequest"));
            }
            return request;
        }

        private TracedLeaseRequest(LeaseRequest delegate, DetachedSpan span) {
            this.delegate = delegate;
            this.span = span;
        }

        @Override
        public ConnectionEndpoint get(Timeout timeout)
                throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return delegate.get(timeout);
            } finally {
                span.complete();
            }
        }

        @Override
        public boolean cancel() {
            return delegate.cancel();
        }
    }
}
