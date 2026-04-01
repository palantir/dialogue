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

package com.palantir.dialogue.core;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Optional;

public record SslStoreMetadata(StoreFileMetadata trustStore, StoreFileMetadata keyStore) {
    private static final SafeLogger log = SafeLoggerFactory.get(SslStoreMetadata.class);

    public SslStoreMetadata() {
        this(StoreFileMetadata.empty(), StoreFileMetadata.empty());
    }

    public static SslStoreMetadata of(SslConfiguration sslConfiguration) {
        Path trustStorePath = sslConfiguration.trustStorePath();
        Optional<Path> keyStorePath = sslConfiguration.keyStorePath();

        try {
            StoreFileMetadata trustMd = new StoreFileMetadata(Files.size(trustStorePath), hashFile(trustStorePath));
            StoreFileMetadata keyMd;
            if (keyStorePath.isPresent()) {
                keyMd = new StoreFileMetadata(Files.size(keyStorePath.get()), hashFile(keyStorePath.get()));
            } else {
                keyMd = StoreFileMetadata.empty();
            }
            return new SslStoreMetadata(trustMd, keyMd);
        } catch (IOException e) {
            log.warn("Could not read from key material", e);
            throw new SafeUncheckedIoException(e);
        }
    }

    private static HashCode hashFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return Hashing.murmur3_128().hashBytes(bytes);
    }

    public String hash() {
        byte[] trust = trustStore.hash().asBytes();
        byte[] key = keyStore.hash().asBytes();
        byte[] combined = new byte[trust.length + key.length];
        System.arraycopy(trust, 0, combined, 0, trust.length);
        System.arraycopy(key, 0, combined, trust.length, key.length);
        return HexFormat.of().formatHex(combined);
    }

    public record StoreFileMetadata(long sizeBytes, HashCode hash) {
        public static StoreFileMetadata empty() {
            return new StoreFileMetadata(0, HashCode.fromBytes(new byte[1]));
        }
    }
}
