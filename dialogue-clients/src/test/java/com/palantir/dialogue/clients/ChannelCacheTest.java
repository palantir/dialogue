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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestOnlyCertificates;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChannelCacheTest {

    private static final char[] STORE_PASSWORD = "keystore".toCharArray();

    private final ChannelCache cache = ChannelCache.createEmptyCache();
    private final ServiceConfiguration serviceConf = ServiceConfiguration.builder()
            .security(TestConfigurations.SSL_CONFIG)
            .build();

    private Undertow undertow;
    private String uri;
    private HttpHandler undertowHandler;

    @BeforeEach
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(
                        0, "localhost", new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();

        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        uri = String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }

    @AfterEach
    public void after() {
        undertow.stop();
    }

    @Test
    void identical_requests_are_hits() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        assertThat(cacheResult).isSameAs(cacheResult2);
    }

    @Test
    void different_channel_name_is_miss() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName2")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
        assertThat(cache.toString()).contains("apacheCache.size=2");
    }

    @Test
    void different_dns_resolver_new_instance() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(_hostname -> ImmutableSet.of())
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
    }

    @Test
    void different_ssl_store_hash_new_instance() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .sslStoreHash("hash-one")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .sslStoreHash("hash-two")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
        assertThat(cache.toString()).contains("apacheCache.size=1");
    }

    @Test
    void new_config_evicts_client_but_old_one_is_still_usable() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(ServiceConfiguration.builder()
                        .from(serviceConf)
                        .enableHttp2(false)
                        .build())
                .channelName("channelName")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
        assertThat(cache.toString()).contains("apacheCache.size=1");

        undertowHandler = exchange -> {
            exchange.setStatusCode(200);
        };

        // Some clients might still be using this channel even though we're evicting it from the cache, so it's
        // important that the evicted client is still usable. Otherwise, we get support tickets like PDS-118523
        // where outgoing requests fail with 'Connection pool shut down'
        SampleServiceBlocking evictedClient = sampleServiceBlocking(cacheResult.client());
        evictedClient.voidToVoid();

        SampleServiceBlocking client2 = sampleServiceBlocking(cacheResult2.client());
        client2.voidToVoid();
    }

    @Test
    void keystore_and_truststore_changes_are_reflected_in_created_client(@TempDir Path tempDir) throws Exception {
        Path trustStoreCopy = tempDir.resolve("trustStore-copy.jks");
        Path keyStoreCopy = tempDir.resolve("keyStore-copy.jks");
        Files.copy(pathFromSslConfig("trustStorePath"), trustStoreCopy);
        Files.copy(pathFromSslConfig("keyStorePath"), keyStoreCopy);

        SslConfiguration copiedSslConfig = SslConfiguration.of(trustStoreCopy, keyStoreCopy, "keystore");
        ServiceConfiguration copiedServiceConfig =
                ServiceConfiguration.builder().security(copiedSslConfig).build();

        ImmutableApacheClientRequest request = apacheRequest(copiedServiceConfig, "mutated-store-test");
        ChannelCache.ApacheCacheEntry initialEntry = cache.getApacheClient(request);

        TestOnlyCertificates.GeneratedKeyPair replacement = TestOnlyCertificates.generate("localhost");
        overwriteTrustStore(trustStoreCopy, replacement);
        overwriteKeyStore(keyStoreCopy, replacement);
        /*
        SslConfiguration moreCopySsl = SslConfiguration.of(trustStoreCopy, keyStoreCopy, "keystore");
        ServiceConfiguration moreConfig =
                ServiceConfiguration.builder().security(moreCopySsl).build();
        ImmutableApacheClientRequest request2 = apacheRequest(moreConfig, "mutated-store-test-2");*/

        String fingerprintOnDisk = certificateFingerprint(readOnlyCertificateFromStore(trustStoreCopy));
        assertThat(fingerprintOnDisk)
                .as("sanity check that test truststore mutation produced a new certificate")
                .isNotIn(trustedFingerprints(initialEntry));

        Thread.sleep(10000);

        ChannelCache.ApacheCacheEntry afterMutationEntry = cache.getApacheClient(request);
        assertThat(trustedFingerprints(afterMutationEntry))
                .as("Expected trust/keystore material to be reloaded after file mutation")
                .contains(fingerprintOnDisk);
    }

    private SampleServiceBlocking sampleServiceBlocking(ApacheHttpClientChannels.CloseableClient apache) {
        return SampleServiceBlocking.of(
                ApacheHttpClientChannels.createSingleUri(uri, apache),
                DefaultConjureRuntime.builder().build());
    }

    private static ImmutableApacheClientRequest apacheRequest(
            ServiceConfiguration inputServiceConf, String channelName) {
        return ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(inputServiceConf)
                .channelName(channelName)
                .build();
    }

    private static Set<String> trustedFingerprints(ChannelCache.ApacheCacheEntry entry) {
        return Arrays.stream(entry.conf().trustManager().getAcceptedIssuers())
                .map(ChannelCacheTest::certificateFingerprint)
                .collect(Collectors.toSet());
    }

    private static String certificateFingerprint(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509Certificate readOnlyCertificateFromStore(Path storePath)
            throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream inputStream = Files.newInputStream(storePath)) {
            keyStore.load(inputStream, STORE_PASSWORD);
        }
        String alias = keyStore.aliases().nextElement();
        Certificate certificate = keyStore.getCertificate(alias);
        return (X509Certificate) certificate;
    }

    private static void overwriteTrustStore(Path trustStorePath, TestOnlyCertificates.GeneratedKeyPair replacement)
            throws IOException, GeneralSecurityException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, STORE_PASSWORD);
        trustStore.setCertificateEntry("self", replacement.certificate());
        try (OutputStream outputStream = Files.newOutputStream(trustStorePath)) {
            trustStore.store(outputStream, null);
        }
    }

    private static void overwriteKeyStore(Path keyStorePath, TestOnlyCertificates.GeneratedKeyPair replacement)
            throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, STORE_PASSWORD);
        keyStore.setKeyEntry(
                "key", replacement.privateKey(), STORE_PASSWORD, new Certificate[] {replacement.certificate()});
        try (OutputStream outputStream = Files.newOutputStream(keyStorePath)) {
            keyStore.store(outputStream, STORE_PASSWORD);
        }
    }

    private static Path pathFromSslConfig(String methodName) {
        try {
            Method method = TestConfigurations.SSL_CONFIG.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(TestConfigurations.SSL_CONFIG);
            if (value instanceof Path path) {
                return path;
            }
            if (value instanceof Optional<?> maybePath
                    && maybePath.isPresent()
                    && maybePath.get() instanceof Path path) {
                return path;
            }
            throw new IllegalStateException("Unsupported SSL_CONFIG path type: " + value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private enum StubDnsResolver implements DialogueDnsResolver {
        INSTANCE;

        @Override
        public ImmutableSet<InetAddress> resolve(String hostname) {
            try {
                return ImmutableSet.copyOf(InetAddress.getAllByName(hostname));
            } catch (UnknownHostException ignored) {
                return ImmutableSet.of();
            }
        }
    }
}
