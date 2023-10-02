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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
@SuppressWarnings("PreferJavaTimeOverload") // perf sensitive
final class InstrumentedPoolingHttpClientConnectionManager
        implements HttpClientConnectionManager, ConnPoolControl<HttpRoute> {

    private static final SafeLogger log = SafeLoggerFactory.get(InstrumentedPoolingHttpClientConnectionManager.class);

    private final PoolingHttpClientConnectionManager manager;
    private final TaggedMetricRegistry registry;
    private final String clientName;
    private final Timer connectTimerSuccess;
    private final Timer connectTimerFailure;
    private volatile boolean closed;

    InstrumentedPoolingHttpClientConnectionManager(
            PoolingHttpClientConnectionManager manager, TaggedMetricRegistry registry, String clientName) {
        this.manager = manager;
        this.registry = registry;
        this.clientName = clientName;
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        this.connectTimerSuccess = metrics.connectionCreate()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionCreate_Result.SUCCESS)
                .build();
        this.connectTimerFailure = metrics.connectionCreate()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionCreate_Result.FAILURE)
                .build();
    }

    @Override
    public void close() {
        if (!closed) {
            log.warn(
                    "Dialogue ConnectionManager close invoked unexpectedly and ignored",
                    SafeArg.of("clientName", clientName),
                    new SafeRuntimeException("stacktrace"));
            // Note: manager.close is not invoked here, see closeUnderlyingConnectionManager.
        }
    }

    @Override
    public void close(CloseMode closeMode) {
        if (!closed) {
            log.warn(
                    "Dialogue ConnectionManager close invoked unexpectedly and ignored",
                    SafeArg.of("clientName", clientName),
                    SafeArg.of("closeMode", closeMode),
                    new SafeRuntimeException("stacktrace"));
            // Note: manager.close is not invoked here, see closeUnderlyingConnectionManager.
        }
    }

    /**
     * This method is used to close the underlying connection manager, while the {@link #close()} methods are
     * overridden specifically not to do so in order to avoid unexpected closure in MainClientExec when
     * an Error is encountered due to HTTPCLIENT-1924.
     * https://github.com/apache/httpcomponents-client/blob/5b61e132c3871ddfa967ab21b3af5d6d738bc6e8/
     * httpclient5/src/main/java/org/apache/hc/client5/http/impl/classic/MainClientExec.java#L161-L164
     * Note that MainClientExec pool self-closure will likely leak a connection each time it occurs, however
     * dialogue bounds connections to Integer.MAX_VALUE, so this is preferable over.
     */
    void closeUnderlyingConnectionManager() {
        if (!closed) {
            closed = true;
            manager.close();
        }
    }

    @Override
    public LeaseRequest lease(String id, HttpRoute route, Timeout requestTimeout, Object state) {
        return manager.lease(id, route, requestTimeout, state);
    }

    @Override
    public void release(ConnectionEndpoint endpoint, Object state, TimeValue keepAlive) {
        manager.release(endpoint, state, keepAlive);
    }

    @Override
    public void connect(ConnectionEndpoint endpoint, TimeValue connectTimeout, HttpContext context) throws IOException {
        long beginNanos = System.nanoTime();
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ConnectionManager.connect")) {
            manager.connect(endpoint, connectTimeout, context);
            connectTimerSuccess.update(System.nanoTime() - beginNanos, TimeUnit.NANOSECONDS);
        } catch (Throwable throwable) {
            connectTimerFailure.update(System.nanoTime() - beginNanos, TimeUnit.NANOSECONDS);
            DialogueClientMetrics.of(registry)
                    .connectionCreateError()
                    .clientName(clientName)
                    .cause(throwable.getClass().getSimpleName())
                    .build()
                    .mark();

            if (log.isDebugEnabled()) {
                log.debug("Failed to connect to endpoint", SafeArg.of("clientName", clientName), throwable);
            }

            throw throwable;
        }
    }

    @Override
    public void upgrade(ConnectionEndpoint endpoint, HttpContext context) throws IOException {
        manager.upgrade(endpoint, context);
    }

    @Override
    public void closeIdle(TimeValue idleTime) {
        manager.closeIdle(idleTime);
    }

    @Override
    public void closeExpired() {
        manager.closeExpired();
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
        return "InstrumentedPoolingHttpClientConnectionManager{" + manager + '}';
    }
}
