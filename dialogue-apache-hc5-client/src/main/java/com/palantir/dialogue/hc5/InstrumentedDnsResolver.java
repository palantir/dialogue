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

import com.codahale.metrics.Meter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeExceptions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.hc.client5.http.DnsResolver;

/** {@link DnsResolver} wrapper which adds tracing spans. */
final class InstrumentedDnsResolver implements DnsResolver {

    private static final SafeLogger log = SafeLoggerFactory.get(InstrumentedDnsResolver.class);
    private final DnsResolver delegate;
    private final Optional<DialogueDnsResolver> dialogueDnsResolver;
    private final Meter errorMeter;
    private final String clientName;

    InstrumentedDnsResolver(
            DnsResolver delegate,
            Optional<DialogueDnsResolver> dialogueDnsResolver,
            String clientName,
            TaggedMetricRegistry metricRegistry) {
        this.delegate = delegate;
        this.dialogueDnsResolver = dialogueDnsResolver;
        this.clientName = clientName;
        this.errorMeter = DialogueClientMetrics.of(metricRegistry).connectionResolutionError(clientName);
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        // Snapshot whether debug logging is enabled because it may change mid-execution
        boolean debugLoggingEnabled = log.isDebugEnabled();
        // Avoid unnecessary timer syscall overhead when debug logging is not enabled
        long startNanos = debugLoggingEnabled ? System.nanoTime() : -1L;
        try (CloseableTracer ignored = CloseableTracer.startSpan("DnsResolver.resolve")) {
            InetAddress[] resolved = internalHostnameResolution(host);
            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolve({}) on client {} produced '{}' ({} results) after {} ns",
                        UnsafeArg.of("host", host),
                        SafeArg.of("client", clientName),
                        resolved == null ? SafeArg.of("resolved", "null") : UnsafeArg.of("resolved", resolved),
                        SafeArg.of("numResolved", resolved == null ? 0 : resolved.length),
                        SafeArg.of("durationNanos", durationNanos));
            }
            return resolved;
        } catch (Throwable t) {
            recordFailure();

            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolve({}) on client {} failed after {} ns",
                        UnsafeArg.of("host", host),
                        SafeArg.of("client", clientName),
                        SafeArg.of("durationNanos", durationNanos),
                        t);
            }
            throw t;
        }
    }

    private InetAddress[] internalHostnameResolution(String host) throws UnknownHostException {
        if (dialogueDnsResolver.isPresent()) {
            DialogueDnsResolver resolver = dialogueDnsResolver.get();
            ImmutableSet<InetAddress> resolved = resolver.resolve(Strings.nullToEmpty(host));
            if (resolved.isEmpty()) {
                throw new SafeUnknownHostException(
                        "Failed to resolve host", SafeArg.of("client", clientName), UnsafeArg.of("host", host));
            }
            return resolved.toArray(InetAddress[]::new);
        } else {
            return delegate.resolve(host);
        }
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        // Snapshot whether debug logging is enabled because it may change mid-execution
        boolean debugLoggingEnabled = log.isDebugEnabled();
        // Avoid unnecessary timer syscall overhead when debug logging is not enabled
        long startNanos = debugLoggingEnabled ? System.nanoTime() : -1L;
        try (CloseableTracer ignored = CloseableTracer.startSpan("DnsResolver.resolveCanonicalHostname")) {
            String resolved = delegate.resolveCanonicalHostname(host);
            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolveCanonicalHostname({}) on client {} produced '{}' after {} ns",
                        UnsafeArg.of("host", host),
                        SafeArg.of("client", clientName),
                        UnsafeArg.of("resolved", resolved),
                        SafeArg.of("durationNanos", durationNanos));
            }
            return resolved;
        } catch (Throwable t) {
            recordFailure();

            if (debugLoggingEnabled) {
                long durationNanos = System.nanoTime() - startNanos;
                log.debug(
                        "DnsResolver.resolveCanonicalHostname({}) on client {} failed after {} ns",
                        UnsafeArg.of("host", host),
                        SafeArg.of("client", clientName),
                        SafeArg.of("durationNanos", durationNanos),
                        t);
            }
            throw t;
        }
    }

    private void recordFailure() {
        errorMeter.mark();
    }

    @Override
    public String toString() {
        return "InstrumentedDnsResolver{" + delegate + '}';
    }

    private static final class SafeUnknownHostException extends UnknownHostException implements SafeLoggable {
        @CompileTimeConstant
        private final String logMessage;

        private final List<Arg<?>> arguments;

        SafeUnknownHostException(@CompileTimeConstant String message, Arg<?>... arguments) {
            super(SafeExceptions.renderMessage(message, arguments));
            this.logMessage = message;
            this.arguments = Collections.unmodifiableList(Arrays.asList(arguments));
        }

        @Override
        public @Safe String getLogMessage() {
            return logMessage;
        }

        @Override
        public List<Arg<?>> getArgs() {
            return arguments;
        }
    }
}
