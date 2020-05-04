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

package com.palantir.dialogue.hc4;

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.HttpClientConnection;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.protocol.HttpContext;

/** Thin shim around a {@link HttpClientConnectionManager} instance to provide safe-loggable exception messages. */
final class SafeLoggingHttpClientConnectionManager implements HttpClientConnectionManager, Closeable {

    private final HttpClientConnectionManager delegate;
    private final AtomicReference<Throwable> closedLocation = new AtomicReference<>();

    SafeLoggingHttpClientConnectionManager(HttpClientConnectionManager delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "HttpClientConnectionManager is required");
    }

    @Override
    public ConnectionRequest requestConnection(HttpRoute route, Object state) {
        try {
            return delegate.requestConnection(route, state);
        } catch (IllegalStateException e) {
            Throwable closedLocationSnapshot = closedLocation.get();
            if (closedLocationSnapshot != null) {
                SafeIllegalStateException exception = new SafeIllegalStateException("Connection pool shut down", e);
                exception.addSuppressed(closedLocationSnapshot);
                throw exception;
            }
            throw e;
        }
    }

    @Override
    public void releaseConnection(HttpClientConnection conn, Object newState, long validDuration, TimeUnit timeUnit) {
        delegate.releaseConnection(conn, newState, validDuration, timeUnit);
    }

    @Override
    public void connect(HttpClientConnection conn, HttpRoute route, int connectTimeout, HttpContext context)
            throws IOException {
        delegate.connect(conn, route, connectTimeout, context);
    }

    @Override
    public void upgrade(HttpClientConnection conn, HttpRoute route, HttpContext context) throws IOException {
        delegate.upgrade(conn, route, context);
    }

    @Override
    public void routeComplete(HttpClientConnection conn, HttpRoute route, HttpContext context) throws IOException {
        delegate.routeComplete(conn, route, context);
    }

    @Override
    public void closeIdleConnections(long idletime, TimeUnit timeUnit) {
        delegate.closeIdleConnections(idletime, timeUnit);
    }

    @Override
    public void closeExpiredConnections() {
        delegate.closeExpiredConnections();
    }

    @Override
    public void shutdown() {
        if (closedLocation.get() == null
                && closedLocation.compareAndSet(null, new SafeRuntimeException("Connection pool closed here"))) {
            delegate.shutdown();
        }
    }

    @Override
    public void close() throws IOException {
        if (closedLocation.get() == null
                && closedLocation.compareAndSet(null, new SafeRuntimeException("Connection pool closed here"))) {
            if (delegate instanceof Closeable) {
                ((Closeable) delegate).close();
            } else {
                delegate.shutdown();
            }
        }
    }

    @Override
    public String toString() {
        return "SafeLoggingHttpClientConnectionManager{delegate=" + delegate + ", closedLocation=" + closedLocation
                + '}';
    }
}
