/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.dialogue.hc5.DialogueClientMetrics.ConnectionConnect_Result;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIoException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http.ssl.TlsCiphers;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Timeout;

/**
 * InstrumentedSslConnectionSocketFactory is based closely on {@link SSLConnectionSocketFactory}, with two changes
 * described below.
 * <ol>
 *     <li>{@link #rawSocketCreator} provided for socks proxy support.</li>
 *     <li>{@link #connectSocket(Socket, HttpHost, InetSocketAddress, InetSocketAddress, Timeout, Object, HttpContext)}
 *     overridden to add timing metrics around {@link Socket#connect(SocketAddress, int)}</li>
 * </ol>
 * The implementation of the connectSocket override borrows heavily from the original implementation of
 * {@link SSLConnectionSocketFactory} from httpclient5-5.2.1 licensed under Apache 2.0 in order to apply
 * instrumentation without interfering with behavior.
 */
final class InstrumentedSslConnectionSocketFactory extends SSLConnectionSocketFactory {
    private static final SafeLogger log = SafeLoggerFactory.get(InstrumentedSslConnectionSocketFactory.class);

    private final Supplier<Socket> rawSocketCreator;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final String clientName;

    private final Timer connectTimerSuccess;
    private final Timer connectTimerFailure;

    InstrumentedSslConnectionSocketFactory(
            String clientName,
            DialogueClientMetrics dialogueClientMetrics,
            SSLSocketFactory socketFactory,
            String[] supportedProtocols,
            String[] supportedCipherSuites,
            HostnameVerifier hostnameVerifier,
            Supplier<Socket> rawSocketCreator) {
        super(socketFactory, supportedProtocols, supportedCipherSuites, hostnameVerifier);
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.clientName = clientName;
        this.rawSocketCreator = rawSocketCreator;
        this.connectTimerSuccess = dialogueClientMetrics
                .connectionConnect()
                .clientName(clientName)
                .result(ConnectionConnect_Result.SUCCESS)
                .build();
        this.connectTimerFailure = dialogueClientMetrics
                .connectionConnect()
                .clientName(clientName)
                .result(ConnectionConnect_Result.FAILURE)
                .build();
    }

    @Override
    public Socket createSocket(HttpContext _context) {
        return rawSocketCreator.get();
    }

    @Override
    public Socket connectSocket(
            Socket socket,
            HttpHost host,
            InetSocketAddress remoteAddress,
            InetSocketAddress localAddress,
            Timeout connectTimeout,
            Object attachment,
            HttpContext context)
            throws IOException {
        Preconditions.checkNotNull(host, "host is required");
        Preconditions.checkNotNull(remoteAddress, "remoteAddress is required");
        Socket sock = socket != null ? socket : createSocket(context);
        if (localAddress != null) {
            sock.bind(localAddress);
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Connecting socket to {} ({}) with timeout {}",
                        SafeArg.of("clientName", clientName),
                        UnsafeArg.of("remoteAddress", remoteAddress),
                        SafeArg.of("connectTimeout", connectTimeout));
            }
            // Run this under a doPrivileged to support lib users that run under a SecurityManager this allows granting
            // connect permissions only to this library
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    doConnect(
                            sock,
                            remoteAddress,
                            Timeout.defaultsToDisabled(connectTimeout).toMillisecondsIntBound());
                    return null;
                });
            } catch (PrivilegedActionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new SafeIoException("Failed to connect", e, SafeArg.of("clientName", clientName));
            }
        } catch (IOException ex) {
            Closer.closeQuietly(sock);
            throw ex;
        }
        // Setup SSL layering if necessary
        if (sock instanceof SSLSocket) {
            SSLSocket sslsock = (SSLSocket) sock;
            executeHandshake(sslsock, host.getHostName(), attachment);
            return sock;
        }
        return createLayeredSocket(sock, host.getHostName(), remoteAddress.getPort(), attachment, context);
    }

    private void doConnect(Socket sock, InetSocketAddress remoteAddress, int connectTimeoutMillis) throws IOException {
        boolean success = false;
        long startNanos = System.nanoTime();
        try {
            sock.connect(remoteAddress, connectTimeoutMillis);
            success = true;
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            Timer timer = success ? connectTimerSuccess : connectTimerFailure;
            timer.update(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void executeHandshake(SSLSocket sslSocket, String target, Object attachment) throws IOException {
        TlsConfig tlsConfig = attachment instanceof TlsConfig ? (TlsConfig) attachment : TlsConfig.DEFAULT;
        if (supportedProtocols != null) {
            sslSocket.setEnabledProtocols(supportedProtocols);
        } else {
            sslSocket.setEnabledProtocols(TLS.excludeWeak(sslSocket.getEnabledProtocols()));
        }
        if (supportedCipherSuites != null) {
            sslSocket.setEnabledCipherSuites(supportedCipherSuites);
        } else {
            sslSocket.setEnabledCipherSuites(TlsCiphers.excludeWeak(sslSocket.getEnabledCipherSuites()));
        }
        Timeout handshakeTimeout = tlsConfig.getHandshakeTimeout();
        if (handshakeTimeout != null) {
            sslSocket.setSoTimeout(handshakeTimeout.toMillisecondsIntBound());
        }

        prepareSocket(sslSocket);

        sslSocket.startHandshake();
        verifyHostname(sslSocket, target);
    }

    private void verifyHostname(SSLSocket sslsock, String hostname) throws IOException {
        try {
            SSLSession session = sslsock.getSession();
            if (session == null) {
                throw new SSLHandshakeException("SSL session not available");
            }
            verifySession(hostname, session);
        } catch (IOException iox) {
            // close the socket before re-throwing the exception
            Closer.closeQuietly(sslsock);
            throw iox;
        }
    }
}
