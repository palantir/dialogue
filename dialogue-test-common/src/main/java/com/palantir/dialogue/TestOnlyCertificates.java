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

package com.palantir.dialogue;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Utility functionality to generate TLS certificates for unit tests. This utility is neither designed nor
 * maintained for production systems, and should never go anywhere near prod.
 */
@SuppressWarnings("JavaUtilDate")
public final class TestOnlyCertificates {

    public static GeneratedKeyPair generate(String name) {
        X500Principal principal = new X500Principal("CN=" + name);
        KeyPair keyPair = generateKeyPair();

        // Self-signed
        X500Principal signedByPrincipal = principal;
        KeyPair signedByKeyPair = keyPair;

        long begin = System.currentTimeMillis();
        long notAfter = begin + Duration.ofDays(1).toMillis();

        try {
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signedByKeyPair.getPrivate());
            X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
                            signedByPrincipal,
                            BigInteger.ONE,
                            new Date(begin),
                            new Date(notAfter),
                            principal,
                            keyPair.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(
                            Extension.keyUsage,
                            true,
                            new KeyUsage(KeyUsage.digitalSignature + KeyUsage.keyEncipherment))
                    .addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(new KeyPurposeId[] {
                        KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth
                    }))
                    .addExtension(Extension.subjectAlternativeName, false, new DERSequence(new ASN1Encodable[] {
                        new GeneralName(GeneralName.dNSName, name)
                    }))
                    .build(signer);

            return new GeneratedKeyPair(keyPair.getPrivate(), new JcaX509CertificateConverter().getCertificate(holder));
        } catch (CertIOException | GeneralSecurityException | OperatorCreationException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509TrustManager toTrustManager(GeneratedKeyPair keyPair) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("cert", keyPair.certificate());
            tmf.init(trustStore);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            return (X509TrustManager) trustManagers[0];
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SSLContext toContext(GeneratedKeyPair keyPair, boolean includeKeys) {
        try {
            KeyManager[] keyManagers;
            if (includeKeys) {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                keyStore.setKeyEntry("key", keyPair.privateKey(), null, new Certificate[] {keyPair.certificate()});
                kmf.init(keyStore, null);
                keyManagers = kmf.getKeyManagers();
            } else {
                keyManagers = null;
            }

            TrustManager[] trustManagers = new TrustManager[] {toTrustManager(keyPair)};

            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(keyManagers, trustManagers, null);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class GeneratedKeyPair {
        private final PrivateKey privateKey;
        private final X509Certificate certificate;

        private GeneratedKeyPair(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }

        public PrivateKey privateKey() {
            return privateKey;
        }

        public X509Certificate certificate() {
            return certificate;
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private TestOnlyCertificates() {}
}
