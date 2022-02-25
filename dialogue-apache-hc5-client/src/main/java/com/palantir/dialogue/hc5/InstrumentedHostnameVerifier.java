/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/** Simple delegating {@link HostnameVerifier} which records cipher metrics. */
final class InstrumentedHostnameVerifier implements HostnameVerifier {

    private final String clientName;
    private final HostnameVerifier delegate;
    private final DialogueClientMetrics metrics;

    InstrumentedHostnameVerifier(HostnameVerifier delegate, String clientName, TaggedMetricRegistry registry) {
        this.delegate = delegate;
        this.clientName = clientName;
        this.metrics = DialogueClientMetrics.of(registry);
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        boolean result = delegate.verify(hostname, session);
        String cipher = session.getCipherSuite();
        if (CipherSuites.deprecatedCiphers().contains(cipher)) {
            metrics.connectionInsecureCipher()
                    .clientName(clientName)
                    .cipher(cipher)
                    .build()
                    .mark();
        }
        return result;
    }

    @Override
    public String toString() {
        return "InstrumentedHostnameVerifier{clientName='" + clientName + "', delegate=" + delegate + '}';
    }
}
