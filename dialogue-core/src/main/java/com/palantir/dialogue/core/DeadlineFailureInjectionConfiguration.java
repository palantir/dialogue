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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Settings for {@link DeadlineFailureInjectionChannel}, read from the
 * {@value DialogueEnvironmentVariables#INJECT_DEADLINE_FAILURES} environment variable.
 */
final class DeadlineFailureInjectionConfiguration {
    private static final SafeLogger log = SafeLoggerFactory.get(DeadlineFailureInjectionConfiguration.class);

    /** Rate used when no explicit rate is configured. */
    static final int DEFAULT_ONE_IN_EVERY = 100_000;

    /** Interval used when no explicit interval is configured. */
    static final Duration DEFAULT_MINIMUM_FAILURE_INTERVAL = Duration.ofMinutes(15);

    private final int oneInEvery;
    private final Duration minimumFailureInterval;

    private DeadlineFailureInjectionConfiguration(int oneInEvery, Duration minimumFailureInterval) {
        this.oneInEvery = oneInEvery;
        this.minimumFailureInterval = minimumFailureInterval;
    }

    /**
     * Returns the fraction of deadline-enforced requests to fail, expressed as the denominator: a value of
     * {@code 100_000} fails one in every hundred thousand requests. Always at least one.
     */
    int oneInEvery() {
        return oneInEvery;
    }

    /**
     * Returns the minimum time between injected failures on a single channel. This is a ceiling applied on top of
     * {@link #oneInEvery()}, so the observed failure rate is the lower of the two. Never negative; a value of zero
     * disables the cap and lets {@link #oneInEvery()} alone determine the rate.
     */
    Duration minimumFailureInterval() {
        return minimumFailureInterval;
    }

    /**
     * Parses configuration from the environment, returning empty when injection is disabled.
     *
     * <p>The {@value DialogueEnvironmentVariables#INJECT_DEADLINE_FAILURES} variable carries both settings, so that
     * presence alone is enough to enable the feature. The format is {@code <rate>[,<interval>]}:
     *
     * <ul>
     *   <li>unset, empty, or {@code false} — disabled
     *   <li>{@code true} — enabled with both defaults: one in {@value #DEFAULT_ONE_IN_EVERY} requests, at most one
     *       failure every 15 minutes
     *   <li>{@code N} or {@code 1/N} — enabled, failing one in every {@code N} requests, keeping the default interval
     *   <li>{@code N,PT1M} — the same, with an explicit ISO-8601 interval. A bare {@code 1m} or {@code 30s} is also
     *       accepted. An interval of {@code 0} removes the cap entirely, which is useful for local testing where
     *       {@code 1,0} fails every request.
     * </ul>
     *
     * <p>Note that the two settings compose: the interval caps the rate, so {@code 1} on its own still yields only one
     * failure every 15 minutes rather than failing every request.
     *
     * <p>Any unparseable or out-of-range component logs a warning and leaves injection disabled, so that a typo in a
     * deployment cannot silently fail a large share of requests.
     */
    static Optional<DeadlineFailureInjectionConfiguration> fromEnvironment() {
        return fromEnvironmentValue(System.getenv(DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES));
    }

    static Optional<DeadlineFailureInjectionConfiguration> fromEnvironmentValue(@Nullable String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String value = rawValue.trim();
        if (value.isEmpty() || "false".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        if ("true".equalsIgnoreCase(value)) {
            return Optional.of(
                    new DeadlineFailureInjectionConfiguration(DEFAULT_ONE_IN_EVERY, DEFAULT_MINIMUM_FAILURE_INTERVAL));
        }

        int separator = value.indexOf(',');
        String ratePart = separator < 0 ? value : value.substring(0, separator);
        String intervalPart = separator < 0 ? null : value.substring(separator + 1);

        Optional<Integer> oneInEvery = parseRate(ratePart, rawValue);
        if (oneInEvery.isEmpty()) {
            return Optional.empty();
        }
        Optional<Duration> interval = intervalPart == null
                ? Optional.of(DEFAULT_MINIMUM_FAILURE_INTERVAL)
                : parseInterval(intervalPart, rawValue);
        if (interval.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DeadlineFailureInjectionConfiguration(oneInEvery.get(), interval.get()));
    }

    private static Optional<Integer> parseRate(String ratePart, String rawValue) {
        // Accept "1/N" as well as a bare "N" so the rate reads unambiguously in a deployment config.
        String trimmed = ratePart.trim();
        String denominator = trimmed.startsWith("1/") ? trimmed.substring(2) : trimmed;
        int oneInEvery;
        try {
            oneInEvery = Integer.parseInt(denominator.trim());
        } catch (NumberFormatException e) {
            log.warn(
                    "Ignoring unparseable deadline failure injection rate, injection remains disabled",
                    SafeArg.of("environmentVariable", DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES),
                    UnsafeArg.of("value", rawValue),
                    e);
            return Optional.empty();
        }
        if (oneInEvery <= 0) {
            log.warn(
                    "Ignoring non-positive deadline failure injection rate, injection remains disabled",
                    SafeArg.of("environmentVariable", DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES),
                    SafeArg.of("oneInEvery", oneInEvery));
            return Optional.empty();
        }
        return Optional.of(oneInEvery);
    }

    private static Optional<Duration> parseInterval(String intervalPart, String rawValue) {
        String trimmed = intervalPart.trim();
        if (trimmed.isEmpty()) {
            log.warn(
                    "Ignoring empty deadline failure injection interval, injection remains disabled",
                    SafeArg.of("environmentVariable", DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES),
                    UnsafeArg.of("value", rawValue));
            return Optional.empty();
        }
        // A bare "0" is the documented way to remove the cap, but is not valid ISO-8601 on its own.
        if ("0".equals(trimmed)) {
            return Optional.of(Duration.ZERO);
        }
        // Tolerate a bare "1m" or "30s" in addition to ISO-8601, since durations are conventionally written that way
        // in deployment configuration.
        String candidate = trimmed.toUpperCase(Locale.ROOT);
        if (!candidate.startsWith("P")) {
            candidate = "PT" + candidate;
        }
        Duration interval;
        try {
            interval = Duration.parse(candidate);
        } catch (DateTimeParseException e) {
            log.warn(
                    "Ignoring unparseable deadline failure injection interval, injection remains disabled",
                    SafeArg.of("environmentVariable", DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES),
                    UnsafeArg.of("value", rawValue),
                    e);
            return Optional.empty();
        }
        if (interval.isNegative()) {
            log.warn(
                    "Ignoring negative deadline failure injection interval, injection remains disabled",
                    SafeArg.of("environmentVariable", DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES),
                    SafeArg.of("minimumFailureInterval", interval));
            return Optional.empty();
        }
        return Optional.of(interval);
    }
}
