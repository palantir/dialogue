/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.junit.jupiter.api.Test;

final class DialogueTlsPostQuantumHybridKeyExchangeTest {

    @Test
    void removeNamedGroup_preservesOrderOfRemainingGroups() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.removeNamedGroup(
                        new String[] {"X25519MLKEM768", "x25519", "secp256r1"}, "X25519MLKEM768"))
                .containsExactly("x25519", "secp256r1");
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.removeNamedGroup(
                        new String[] {"x25519", "X25519MLKEM768", "secp256r1"}, "X25519MLKEM768"))
                .containsExactly("x25519", "secp256r1");
    }

    @Test
    void removeNamedGroup_isCaseInsensitive() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.removeNamedGroup(
                        new String[] {"x25519mlkem768", "x25519"}, "X25519MLKEM768"))
                .containsExactly("x25519");
    }

    @Test
    void removeNamedGroup_noOpWhenAbsent() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.removeNamedGroup(
                        new String[] {"x25519", "secp256r1"}, "X25519MLKEM768"))
                .containsExactly("x25519", "secp256r1");
    }

    @Test
    void removeNamedGroup_neverReturnsEmpty() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.removeNamedGroup(
                        new String[] {"X25519MLKEM768"}, "X25519MLKEM768"))
                .containsExactly("X25519MLKEM768");
    }

    @Test
    void exclude_allowFlagTrue_leavesNamedGroupsUnchanged() {
        SSLParameters parameters = parametersWithNamedGroups(
                DialogueTlsPostQuantumHybridKeyExchange.HYBRID_PQ_NAMED_GROUP, "x25519", "secp256r1");
        DialogueTlsPostQuantumHybridKeyExchange.excludePostQuantumHybridNamedGroup(parameters, true);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(parameters))
                .containsExactly(DialogueTlsPostQuantumHybridKeyExchange.HYBRID_PQ_NAMED_GROUP, "x25519", "secp256r1");
    }

    @Test
    void exclude_allowFlagTrue_leavesUnsetNamedGroupsUnset() {
        // Doesn't require JDK 20+: a new SSLParameters has no named groups, and older runtimes also report null.
        SSLParameters parameters = new SSLParameters();
        DialogueTlsPostQuantumHybridKeyExchange.excludePostQuantumHybridNamedGroup(parameters, true);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(parameters))
                .isNull();
    }

    @Test
    void exclude_allowFlagFalse_removesHybridGroup() {
        SSLParameters parameters = parametersWithNamedGroups(
                DialogueTlsPostQuantumHybridKeyExchange.HYBRID_PQ_NAMED_GROUP, "x25519", "secp256r1");
        DialogueTlsPostQuantumHybridKeyExchange.excludePostQuantumHybridNamedGroup(parameters, false);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(parameters))
                .containsExactly("x25519", "secp256r1");
    }

    @Test
    void upgrade_excludesHybridGroupFromHandshake() throws IOException {
        assumeNamedGroupsSupported();
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, "localhost", sslContext, new ResponseCodeHandler(200))
                .build();
        undertow.start();
        try {
            int port = ((InetSocketAddress) undertow.getListenerInfo().get(0).getAddress()).getPort();
            // Simulates a JVM whose defaults prefer the hybrid group. JSSE accepts names it doesn't recognize here,
            // so this works on JDKs that predate JEP 527.
            SSLSocketFactory socketFactory = new NamedGroupsSocketFactory(sslContext.getSocketFactory(), new String[] {
                DialogueTlsPostQuantumHybridKeyExchange.HYBRID_PQ_NAMED_GROUP, "x25519", "secp256r1"
            });
            DialogueTlsSocketStrategy strategy = new DialogueTlsSocketStrategy(
                    socketFactory,
                    TlsProtocols.get(),
                    socketFactory.getDefaultCipherSuites(),
                    new DefaultHostnameVerifier());

            try (Socket plain = new Socket(InetAddress.getLoopbackAddress(), port);
                    SSLSocket upgraded = strategy.upgrade(plain, "localhost", port, null, null)) {
                assertThat(upgraded.getSession().isValid()).isTrue();
                assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(upgraded.getSSLParameters()))
                        .containsExactly("x25519", "secp256r1");
            }
        } finally {
            undertow.stop();
        }
    }

    /** Skips the current test on runtimes without {@code SSLParameters#getNamedGroups}, which was added in JDK 20. */
    private static void assumeNamedGroupsSupported() {
        try (SSLSocket unconnected =
                (SSLSocket) SslSocketFactories.createSslSocketFactory(TestConfigurations.SSL_CONFIG)
                        .createSocket()) {
            assumeThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(unconnected.getSSLParameters()))
                    .isNotNull();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SSLParameters parametersWithNamedGroups(String... namedGroups) {
        assumeNamedGroupsSupported();
        SSLParameters parameters = new SSLParameters();
        DialogueTlsPostQuantumHybridKeyExchange.setNamedGroups(parameters, namedGroups);
        return parameters;
    }

    /** Returns sockets whose named groups are preconfigured, as if set by {@code jdk.tls.namedGroups}. */
    private static final class NamedGroupsSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String[] namedGroups;

        NamedGroupsSocketFactory(SSLSocketFactory delegate, String[] namedGroups) {
            this.delegate = delegate;
            this.namedGroups = namedGroups;
        }

        private Socket configure(Socket socket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            SSLParameters parameters = sslSocket.getSSLParameters();
            DialogueTlsPostQuantumHybridKeyExchange.setNamedGroups(parameters, namedGroups);
            sslSocket.setSSLParameters(parameters);
            return sslSocket;
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return configure(delegate.createSocket(address, port, localAddress, localPort));
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }
    }
}
