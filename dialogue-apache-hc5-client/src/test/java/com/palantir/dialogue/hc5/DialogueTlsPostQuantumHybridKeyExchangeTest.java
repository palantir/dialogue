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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.junit.jupiter.api.Test;

final class DialogueTlsPostQuantumHybridKeyExchangeTest {

    private static final String HYBRID = DialogueTlsPostQuantumHybridKeyExchange.HYBRID_PQ_NAMED_GROUP;

    // IANA TLS Supported Groups registry values.
    private static final int X25519_ID = 0x001D;
    private static final int SECP256R1_ID = 0x0017;
    private static final int X25519MLKEM768_ID = 0x11EC;

    private static final int SUPPORTED_GROUPS_EXTENSION = 10;
    private static final int KEY_SHARE_EXTENSION = 51;

    @Test
    void moveNamedGroupToEnd_preservesOrderOfRemainingGroups() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.moveNamedGroupToEnd(
                        new String[] {HYBRID, "x25519", "secp256r1"}, HYBRID))
                .containsExactly("x25519", "secp256r1", HYBRID);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.moveNamedGroupToEnd(
                        new String[] {"x25519", HYBRID, "secp256r1"}, HYBRID))
                .containsExactly("x25519", "secp256r1", HYBRID);
    }

    @Test
    void moveNamedGroupToEnd_isCaseInsensitive() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.moveNamedGroupToEnd(
                        new String[] {"x25519mlkem768", "x25519"}, HYBRID))
                .containsExactly("x25519", "x25519mlkem768");
    }

    @Test
    void moveNamedGroupToEnd_noOpWhenAbsent() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.moveNamedGroupToEnd(
                        new String[] {"x25519", "secp256r1"}, HYBRID))
                .containsExactly("x25519", "secp256r1");
    }

    @Test
    void moveNamedGroupToEnd_noOpWhenAlreadyLast() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.moveNamedGroupToEnd(
                        new String[] {"x25519", "secp256r1", HYBRID}, HYBRID))
                .containsExactly("x25519", "secp256r1", HYBRID);
    }

    @Test
    void moveNamedGroupToEnd_noOpWhenOnlyGroup() {
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.moveNamedGroupToEnd(new String[] {HYBRID}, HYBRID))
                .containsExactly(HYBRID);
    }

    @Test
    void reorder_allowFlagTrue_leavesNamedGroupsUnchanged() {
        SSLParameters parameters = parametersWithNamedGroups(HYBRID, "x25519", "secp256r1");
        DialogueTlsPostQuantumHybridKeyExchange.reorderPostQuantumHybridNamedGroup(parameters, true);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(parameters))
                .containsExactly(HYBRID, "x25519", "secp256r1");
    }

    @Test
    void reorder_allowFlagTrue_leavesUnsetNamedGroupsUnset() {
        // Doesn't require JDK 20+: a new SSLParameters has no named groups, and older runtimes also report null.
        SSLParameters parameters = new SSLParameters();
        DialogueTlsPostQuantumHybridKeyExchange.reorderPostQuantumHybridNamedGroup(parameters, true);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(parameters))
                .isNull();
    }

    @Test
    void reorder_allowFlagFalse_movesHybridGroupToEnd() {
        SSLParameters parameters = parametersWithNamedGroups(HYBRID, "x25519", "secp256r1");
        DialogueTlsPostQuantumHybridKeyExchange.reorderPostQuantumHybridNamedGroup(parameters, false);
        assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(parameters))
                .containsExactly("x25519", "secp256r1", HYBRID);
    }

    @Test
    void upgrade_movesHybridGroupToEnd() throws IOException {
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
            SSLSocketFactory socketFactory = new NamedGroupsSocketFactory(
                    sslContext.getSocketFactory(), new String[] {HYBRID, "x25519", "secp256r1"});
            DialogueTlsSocketStrategy strategy = strategy(socketFactory);

            try (Socket plain = new Socket(InetAddress.getLoopbackAddress(), port);
                    SSLSocket upgraded = strategy.upgrade(plain, "localhost", port, null, null)) {
                assertThat(upgraded.getSession().isValid()).isTrue();
                assertThat(DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(upgraded.getSSLParameters()))
                        .containsExactly("x25519", "secp256r1", HYBRID);
            }
        } finally {
            undertow.stop();
        }
    }

    /**
     * Guards the undocumented JSSE behavior this approach relies on: key shares are sent for at most two kinds of
     * named group, so with the JDK defaults reordered the hybrid group is advertised without its large key share.
     */
    @Test
    void upgrade_initialClientHelloAdvertisesHybridGroupWithoutKeyShare() throws Exception {
        assumeHybridGroupEnabledByDefault();
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        DialogueTlsSocketStrategy strategy = strategy(sslContext.getSocketFactory());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            // Capture the first TLS record from the client, then hang up without responding.
            Future<byte[]> clientHello = executor.submit(() -> {
                try (Socket accepted = server.accept()) {
                    DataInputStream in = new DataInputStream(accepted.getInputStream());
                    byte[] header = new byte[5];
                    in.readFully(header);
                    byte[] record = new byte[((header[3] & 0xFF) << 8) | (header[4] & 0xFF)];
                    in.readFully(record);
                    return record;
                }
            });

            try (Socket plain = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                assertThatThrownBy(() -> strategy.upgrade(plain, "localhost", server.getLocalPort(), null, null))
                        .isInstanceOf(IOException.class);
            }

            Map<Integer, ByteBuffer> extensions = clientHelloExtensions(clientHello.get(10, TimeUnit.SECONDS));
            assertThat(supportedGroups(extensions.get(SUPPORTED_GROUPS_EXTENSION)))
                    .startsWith(X25519_ID, SECP256R1_ID)
                    .endsWith(X25519MLKEM768_ID);
            assertThat(keyShareGroups(extensions.get(KEY_SHARE_EXTENSION))).containsExactly(X25519_ID, SECP256R1_ID);
        } finally {
            executor.shutdownNow();
        }
    }

    /** A server that only accepts the hybrid group must still be reachable, via a HelloRetryRequest. */
    @Test
    void upgrade_succeedsAgainstServerRequiringHybridGroup() throws Exception {
        assumeHybridGroupEnabledByDefault();
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        DialogueTlsSocketStrategy strategy = strategy(sslContext.getSocketFactory());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket)
                sslContext.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            SSLParameters serverParameters = server.getSSLParameters();
            DialogueTlsPostQuantumHybridKeyExchange.setNamedGroups(serverParameters, new String[] {HYBRID});
            server.setSSLParameters(serverParameters);
            Future<?> serverHandshake = executor.submit(() -> {
                try (SSLSocket accepted = (SSLSocket) server.accept()) {
                    accepted.startHandshake();
                    // Wait for the client to finish its side of the handshake before closing.
                    return accepted.getInputStream().read();
                }
            });

            try (Socket plain = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                    SSLSocket upgraded = strategy.upgrade(plain, "localhost", server.getLocalPort(), null, null)) {
                assertThat(upgraded.getSession().getProtocol()).isEqualTo("TLSv1.3");
                upgraded.getOutputStream().write(1);
                upgraded.getOutputStream().flush();
            }
            serverHandshake.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static DialogueTlsSocketStrategy strategy(SSLSocketFactory socketFactory) {
        return new DialogueTlsSocketStrategy(
                socketFactory,
                TlsProtocols.get(),
                socketFactory.getDefaultCipherSuites(),
                new DefaultHostnameVerifier());
    }

    /** Skips the current test unless the JVM's default named groups include the hybrid group (JEP 527). */
    private static void assumeHybridGroupEnabledByDefault() {
        assumeNamedGroupsSupported();
        String[] defaults = DialogueTlsPostQuantumHybridKeyExchange.getNamedGroups(
                SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG)
                        .getDefaultSSLParameters());
        assumeThat(defaults).isNotNull().anySatisfy(group -> assertThat(group).isEqualToIgnoringCase(HYBRID));
    }

    /** Parses a ClientHello handshake message into its extensions, keyed by extension type. */
    private static Map<Integer, ByteBuffer> clientHelloExtensions(byte[] handshake) {
        ByteBuffer buffer = ByteBuffer.wrap(handshake);
        assertThat(buffer.get()).as("handshake type").isEqualTo((byte) 1);
        skip(buffer, 3); // handshake length
        skip(buffer, 2 + 32); // legacy_version, random
        skip(buffer, buffer.get() & 0xFF); // legacy_session_id
        skip(buffer, buffer.getShort() & 0xFFFF); // cipher_suites
        skip(buffer, buffer.get() & 0xFF); // legacy_compression_methods
        ByteBuffer extensions = slice(buffer, buffer.getShort() & 0xFFFF);
        Map<Integer, ByteBuffer> result = new HashMap<>();
        while (extensions.hasRemaining()) {
            int type = extensions.getShort() & 0xFFFF;
            result.put(type, slice(extensions, extensions.getShort() & 0xFFFF));
        }
        return result;
    }

    private static List<Integer> supportedGroups(ByteBuffer extension) {
        ByteBuffer groups = slice(extension, extension.getShort() & 0xFFFF);
        List<Integer> result = new ArrayList<>();
        while (groups.hasRemaining()) {
            result.add(groups.getShort() & 0xFFFF);
        }
        return result;
    }

    private static List<Integer> keyShareGroups(ByteBuffer extension) {
        ByteBuffer shares = slice(extension, extension.getShort() & 0xFFFF);
        List<Integer> result = new ArrayList<>();
        while (shares.hasRemaining()) {
            result.add(shares.getShort() & 0xFFFF);
            skip(shares, shares.getShort() & 0xFFFF); // key_exchange
        }
        return result;
    }

    private static ByteBuffer slice(ByteBuffer buffer, int length) {
        ByteBuffer slice = buffer.slice();
        slice.limit(length);
        skip(buffer, length);
        return slice;
    }

    private static void skip(ByteBuffer buffer, int length) {
        buffer.position(buffer.position() + length);
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
