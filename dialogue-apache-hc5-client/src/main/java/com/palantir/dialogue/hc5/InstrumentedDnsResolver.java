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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.CloseableTracer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/** {@link DnsResolver} wrapper which adds tracing spans. */
final class InstrumentedDnsResolver implements DnsResolver {

    private static final SafeLogger log = SafeLoggerFactory.get(InstrumentedDnsResolver.class);
    private final DnsResolver delegate;

    InstrumentedDnsResolver(DnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        boolean debugLoggingEnabled = log.isDebugEnabled();
        long startNanos = debugLoggingEnabled ? System.nanoTime() : -1L;
        try (CloseableTracer ignored = CloseableTracer.startSpan("DnsResolver.resolve")) {
            InetAddress[] resolved = delegate.resolve(host);
            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolve({}) produced '{}' ({} results) after {} ns",
                        UnsafeArg.of("host", host),
                        resolved == null ? SafeArg.of("resolved", "null") : UnsafeArg.of("resolved", resolved),
                        SafeArg.of("numResolved", resolved == null ? 0 : resolved.length),
                        SafeArg.of("durationNanos", durationNanos));
            }
            return resolved;
        } catch (Throwable t) {
            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolve({}) failed after {} ns",
                        UnsafeArg.of("host", host),
                        SafeArg.of("durationNanos", durationNanos),
                        t);
            }
            throw t;
        }
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        boolean debugLoggingEnabled = log.isDebugEnabled();
        long startNanos = debugLoggingEnabled ? System.nanoTime() : -1L;
        try (CloseableTracer ignored = CloseableTracer.startSpan("DnsResolver.resolveCanonicalHostname")) {
            String resolved = delegate.resolveCanonicalHostname(host);
            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolveCanonicalHostname({}) produced '{}' after {} ns",
                        UnsafeArg.of("host", host),
                        UnsafeArg.of("resolved", resolved),
                        SafeArg.of("durationNanos", durationNanos));
            }
            return resolved;
        } catch (Throwable t) {
            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolveCanonicalHostname({}) failed after {} ns",
                        UnsafeArg.of("host", host),
                        SafeArg.of("durationNanos", durationNanos),
                        t);
            }
            throw t;
        }
    }

    @Override
    public String toString() {
        return "InstrumentedDnsResolver{" + delegate + '}';
    }
}
