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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;

final class ServerTimingParser {

    private static final SafeLogger log = SafeLoggerFactory.get(ServerTimingParser.class);

    /** Splits a header value into individual metrics. */
    private static final Splitter METRIC_SPLITTER =
            Splitter.on(',').trimResults().omitEmptyStrings();

    private static final CharMatcher NUMERIC_MATCHER =
            CharMatcher.inRange('0', '9').or(CharMatcher.anyOf("-."));

    private static final String DURATION = ";dur=";
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    static final long UNKNOWN = -1;

    /**
     * Returns the {@code Server-Timing} duration in nanoseconds for the
     * given operation name or {@link #UNKNOWN} if it cannot be parsed.
     */
    static long getServerDurationNanos(String serverTimingValue, String operation) {
        if (Strings.isNullOrEmpty(serverTimingValue) || !serverTimingValue.contains(operation)) {
            return UNKNOWN;
        }
        try {
            for (String segment : METRIC_SPLITTER.split(serverTimingValue)) {
                if (segment.length() > operation.length() + DURATION.length()
                        && segment.startsWith(operation)
                        && segment.charAt(operation.length()) == ';') {
                    // Ignore any extraneous fields, search for `dur`
                    int durationStart = segment.indexOf(DURATION);
                    if (durationStart < 0) {
                        return UNKNOWN;
                    }
                    int durationValueStart = durationStart + DURATION.length();
                    int durationValueEnd = findNumericSequenceEnd(segment, durationValueStart);
                    String durationValue = segment.substring(durationValueStart, durationValueEnd);
                    double durationMilliseconds = Double.parseDouble(durationValue);
                    return (long) (NANOSECONDS_PER_MILLISECOND * durationMilliseconds);
                }
            }
        } catch (RuntimeException e) {
            // Important not to fail calls even if we fail to parse a timing value
            log.warn("Failed to parse Server-Timing value '{}'", SafeArg.of("serverTiming", serverTimingValue), e);
        }
        return UNKNOWN;
    }

    private static int findNumericSequenceEnd(String string, int beginIndex) {
        int currentIndex = beginIndex;
        while (currentIndex < string.length() && NUMERIC_MATCHER.matches(string.charAt(currentIndex))) {
            currentIndex++;
        }
        return currentIndex;
    }

    private ServerTimingParser() {}
}
