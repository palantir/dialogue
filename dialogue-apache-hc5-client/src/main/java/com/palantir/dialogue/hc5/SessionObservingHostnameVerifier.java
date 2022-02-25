/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.dialogue.hc5;

import java.util.function.Consumer;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/** Simple delegating {@link HostnameVerifier} implementation which allows the session to be observed. */
final class SessionObservingHostnameVerifier implements HostnameVerifier {

    private final HostnameVerifier delegate;
    private final Consumer<SSLSession> sslSessionConsumer;

    SessionObservingHostnameVerifier(HostnameVerifier delegate, Consumer<SSLSession> sslSessionConsumer) {
        this.delegate = delegate;
        this.sslSessionConsumer = sslSessionConsumer;
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        boolean result = delegate.verify(hostname, session);
        sslSessionConsumer.accept(session);
        return result;
    }

    @Override
    public String toString() {
        return "InstrumentedHostnameVerifier{delegate=" + delegate + ", sslSessionConsumer=" + sslSessionConsumer + '}';
    }
}
