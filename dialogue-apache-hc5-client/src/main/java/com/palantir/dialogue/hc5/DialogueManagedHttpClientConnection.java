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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.impl.io.SocketHolder;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.Timeout;

/**
 * Connection which produces behavior matching the default managed connection from httpcore while
 * providing the functionality proposed in
 * <a href="https://issues.apache.org/jira/browse/HTTPCORE-639">HTTPCORE-639</a>.
 */
final class DialogueManagedHttpClientConnection extends DefaultBHttpClientConnection
        implements ManagedHttpClientConnection, Identifiable {

    private final String id;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean availableDataCheckDisabled = new AtomicBoolean();

    @Nullable
    private Timeout socketTimeout;

    DialogueManagedHttpClientConnection(
            String id,
            @Nullable CharsetDecoder charDecoder,
            @Nullable CharsetEncoder charEncoder,
            Http1Config h1Config,
            ContentLengthStrategy incomingContentStrategy,
            ContentLengthStrategy outgoingContentStrategy,
            HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        super(
                h1Config,
                charDecoder,
                charEncoder,
                incomingContentStrategy,
                outgoingContentStrategy,
                requestWriterFactory,
                responseParserFactory);
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void bind(SocketHolder socketHolder) throws IOException {
        if (closed.get()) {
            socketHolder.getSocket().close();
            throw new InterruptedIOException("Connection already shutdown");
        }
        super.bind(socketHolder);
        socketTimeout = Timeout.ofMilliseconds(socketHolder.getSocket().getSoTimeout());
    }

    @Override
    public void bind(Socket socket) throws IOException {
        super.bind(new SocketHolder(socket));
        socketTimeout = Timeout.ofMilliseconds(socket.getSoTimeout());
    }

    @Nullable
    @Override
    public Socket getSocket() {
        SocketHolder socketHolder = getSocketHolder();
        return socketHolder != null ? socketHolder.getSocket() : null;
    }

    @Nullable
    @Override
    public SSLSession getSSLSession() {
        Socket socket = getSocket();
        return socket instanceof SSLSocket ? ((SSLSocket) socket).getSession() : null;
    }

    @Override
    public void close() throws IOException {
        if (!closed.getAndSet(true)) {
            super.close();
        }
    }

    @Override
    public void close(CloseMode closeMode) {
        if (!closed.getAndSet(true)) {
            super.close(closeMode);
        }
    }

    @Override
    protected void onResponseReceived(ClassicHttpResponse _response) {}

    @Override
    protected void onRequestSubmitted(ClassicHttpRequest _request) {}

    @Override
    public void passivate() {
        super.setSocketTimeout(Timeout.DISABLED);
    }

    @Override
    public void activate() {
        super.setSocketTimeout(socketTimeout);
    }

    @Override
    public void sendRequestEntity(ClassicHttpRequest request) throws HttpException, IOException {
        boolean disableResponseOutOfOrderCheck = disableResponseOutOfOrderCheck(request)
                // Don't take action if it's already set.
                && !availableDataCheckDisabled.getAndSet(true);
        try {
            super.sendRequestEntity(request);
        } finally {
            if (disableResponseOutOfOrderCheck) {
                availableDataCheckDisabled.set(false);
            }
        }
    }

    private boolean disableResponseOutOfOrderCheck(ClassicHttpRequest request) throws IOException {
        // Plain http connections can rely on Socket.available without attempting a read.
        boolean ssl = ensureOpen().getSocket() instanceof SSLSocket;
        HttpEntity entity = request.getEntity();
        // Don't opt out of this check for binary streamed requests for now.
        boolean repeatable = entity == null || entity.isRepeatable();
        return ssl && repeatable;
    }

    @Override
    public boolean isDataAvailable(Timeout timeout) throws IOException {
        if (Timeout.ONE_MILLISECOND.equals(timeout) && availableDataCheckDisabled.get()) {
            ensureOpen();
            return false;
        }
        return super.isDataAvailable(timeout);
    }
}
