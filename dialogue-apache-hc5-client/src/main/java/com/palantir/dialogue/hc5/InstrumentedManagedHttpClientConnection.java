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
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSession;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** A simple wrapper around a {@link ManagedHttpClientConnection} which provides instrumentation. */
final class InstrumentedManagedHttpClientConnection implements ManagedHttpClientConnection {

    private final ManagedHttpClientConnection delegate;
    private final Timer serverTimingOverhead;
    private final Timer socketBindTimer;
    private final Timer socketBindFailureTimer;

    InstrumentedManagedHttpClientConnection(
            ManagedHttpClientConnection delegate,
            Timer serverTimingOverhead,
            Timer socketBindTimer,
            Timer socketBindFailureTimer) {
        this.delegate = delegate;
        this.serverTimingOverhead = serverTimingOverhead;
        this.socketBindTimer = socketBindTimer;
        this.socketBindFailureTimer = socketBindFailureTimer;
    }

    @Override
    public void bind(Socket socket) throws IOException {
        long startTimeNanos = System.nanoTime();
        try {
            delegate.bind(socket);
            socketBindTimer.update(System.nanoTime() - startTimeNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            socketBindFailureTimer.update(System.nanoTime() - startTimeNanos, TimeUnit.NANOSECONDS);
            throw e;
        }
    }

    @Override
    public Socket getSocket() {
        return delegate.getSocket();
    }

    @Override
    public SSLSession getSSLSession() {
        return delegate.getSSLSession();
    }

    @Override
    public void passivate() {
        delegate.passivate();
    }

    @Override
    public void activate() {
        delegate.activate();
    }

    @Override
    public boolean isConsistent() {
        return delegate.isConsistent();
    }

    @Override
    public void sendRequestHeader(ClassicHttpRequest request) throws HttpException, IOException {
        delegate.sendRequestHeader(request);
    }

    @Override
    public void terminateRequest(ClassicHttpRequest request) throws HttpException, IOException {
        delegate.terminateRequest(request);
    }

    @Override
    public void sendRequestEntity(ClassicHttpRequest request) throws HttpException, IOException {
        delegate.sendRequestEntity(request);
    }

    @Override
    public ClassicHttpResponse receiveResponseHeader() throws HttpException, IOException {
        long startTimeNanos = System.nanoTime();
        ClassicHttpResponse response = delegate.receiveResponseHeader();
        recordTimingDelta(response, startTimeNanos);
        return response;
    }

    // Report metrics describing the difference between client and server request time.
    // This wraps the client as closely to the wire as possible in order to account for every
    // millisecond we reasonably can.
    @SuppressWarnings("PreferJavaTimeOverload")
    private void recordTimingDelta(ClassicHttpResponse httpClientResponse, long startTimeNanos) {
        Header serverTimingValue = httpClientResponse.getFirstHeader(HttpHeaders.SERVER_TIMING);
        if (serverTimingValue != null) {
            long clientDurationNanos = System.nanoTime() - startTimeNanos;
            long serverNanos = ServerTimingParser.getServerDurationNanos(serverTimingValue.getValue(), "server");
            long deltaNanos = clientDurationNanos - serverNanos;
            // avoid reporting negative values if the server yields an incorrect value.
            // In the case of large request bodies the server may have begun counting prior to
            // the client. Theres's not a great way to measure the difference in that case
            // so values may not be recorded.
            if (serverNanos >= 0 && deltaNanos >= 0) {
                serverTimingOverhead.update(deltaNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    @Override
    public void receiveResponseEntity(ClassicHttpResponse response) throws HttpException, IOException {
        delegate.receiveResponseEntity(response);
    }

    @Override
    public boolean isDataAvailable(Timeout timeout) throws IOException {
        return delegate.isDataAvailable(timeout);
    }

    @Override
    public boolean isStale() throws IOException {
        return delegate.isStale();
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return delegate.getEndpointDetails();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return delegate.getRemoteAddress();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return delegate.getProtocolVersion();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public Timeout getSocketTimeout() {
        return delegate.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(Timeout timeout) {
        delegate.setSocketTimeout(timeout);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void close(CloseMode closeMode) {
        delegate.close(closeMode);
    }

    @Override
    public String toString() {
        return "InstrumentedManagedHttpClientConnection{" + delegate + '}';
    }
}
