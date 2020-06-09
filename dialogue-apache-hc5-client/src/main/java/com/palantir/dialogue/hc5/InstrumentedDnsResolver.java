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

import com.palantir.tracing.CloseableTracer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/** {@link DnsResolver} wrapper which adds tracing spans. */
final class InstrumentedDnsResolver implements DnsResolver {

    private final DnsResolver delegate;

    InstrumentedDnsResolver(DnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        try (CloseableTracer ignored = CloseableTracer.startSpan("DnsResolver.resolve")) {
            return delegate.resolve(host);
        }
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        try (CloseableTracer ignored = CloseableTracer.startSpan("DnsResolver.resolveCanonicalHostname")) {
            return delegate.resolveCanonicalHostname(host);
        }
    }

    @Override
    public String toString() {
        return "InstrumentedDnsResolver{" + delegate + '}';
    }
}
