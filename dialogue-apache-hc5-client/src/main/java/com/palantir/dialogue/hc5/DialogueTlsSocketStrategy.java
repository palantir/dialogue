/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.ssl.HttpClientHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link DialogueTlsSocketStrategy} is based closely on
 * {@code org.apache.hc.client5.http.ssl.AbstractClientTlsStrategy}, except that it only requires a
 * {@link SSLSocketFactory} rather than an {@link javax.net.ssl.SSLContext}.
 * We only implement the minimal required {@link TlsSocketStrategy} interface rather than
 * {@link org.apache.hc.core5.http.nio.ssl.TlsStrategy}, which isn't required by socket-based clients.
 */
final class DialogueTlsSocketStrategy implements TlsSocketStrategy {

    private final SSLSocketFactory sslSocketFactory;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final HostnameVerifier hostnameVerifier;

    DialogueTlsSocketStrategy(
            SSLSocketFactory sslSocketFactory,
            String[] supportedProtocols,
            String[] supportedCipherSuites,
            HostnameVerifier hostnameVerifier) {
        this.sslSocketFactory = Preconditions.checkNotNull(sslSocketFactory, "SSLSocketFactory is required");
        this.supportedProtocols = Preconditions.checkNotNull(supportedProtocols, "supportedProtocols is required");
        this.supportedCipherSuites =
                Preconditions.checkNotNull(supportedCipherSuites, "supportedCipherSuites is required");
        this.hostnameVerifier = Preconditions.checkNotNull(hostnameVerifier, "hostnameVerifier is required");
    }

    @Override
    public SSLSocket upgrade(Socket socket, String target, int port, Object attachment, HttpContext _context)
            throws IOException {
        SSLSocket upgradedSocket = (SSLSocket) sslSocketFactory.createSocket(socket, target, port, false);
        try {
            executeHandshake(upgradedSocket, target, attachment);
            return upgradedSocket;
        } catch (IOException | RuntimeException ex) {
            Closer.closeQuietly(upgradedSocket);
            throw ex;
        }
    }

    private void executeHandshake(SSLSocket upgradedSocket, String target, Object attachment) throws IOException {
        SSLParameters sslParameters = upgradedSocket.getSSLParameters();
        sslParameters.setProtocols(supportedProtocols);
        sslParameters.setCipherSuites(supportedCipherSuites);

        // If we want to enable the builtin hostname verification support:
        // sslParameters.setEndpointIdentificationAlgorithm(URIScheme.HTTPS.id);

        upgradedSocket.setSSLParameters(sslParameters);

        if (attachment instanceof TlsConfig) {
            TlsConfig tlsConfig = (TlsConfig) attachment;
            Timeout handshakeTimeout = tlsConfig.getHandshakeTimeout();
            if (handshakeTimeout != null) {
                upgradedSocket.setSoTimeout(handshakeTimeout.toMillisecondsIntBound());
            }
        }

        upgradedSocket.startHandshake();
        verifySession(target, upgradedSocket.getSession(), hostnameVerifier);
    }

    private static void verifySession(String hostname, SSLSession sslsession, HostnameVerifier verifier)
            throws SSLException {
        if (verifier instanceof HttpClientHostnameVerifier) {
            X509Certificate x509Certificate = getX509Certificate(sslsession);
            ((HttpClientHostnameVerifier) verifier).verify(hostname, x509Certificate);
        } else if (!verifier.verify(hostname, sslsession)) {
            throw new SSLPeerUnverifiedException("Certificate doesn't match any of the subject alternative names");
        }
    }

    private static X509Certificate getX509Certificate(SSLSession sslsession) throws SSLPeerUnverifiedException {
        Certificate[] certs = sslsession.getPeerCertificates();
        if (certs.length < 1) {
            throw new SSLPeerUnverifiedException("Peer certificate chain is empty");
        }
        Certificate peerCertificate = certs[0];
        if (peerCertificate instanceof X509Certificate) {
            return (X509Certificate) peerCertificate;
        }
        throw new SSLPeerUnverifiedException("Unexpected certificate type: " + peerCertificate.getType());
    }
}
