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

package com.palantir.dialogue.core;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.List;

/**
 * Supported node selection strategies which can either be user provided or received over the wire from servers.
 * Separate from {@link NodeSelectionStrategy} to allow us to more easily iterate on strategies and support unknown
 * strategies coming in over the wire.
 */
enum DialogueNodeSelectionStrategy {
    PIN_UNTIL_ERROR,
    PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE,
    BALANCED,
    UNKNOWN;

    private static final SafeLogger log = SafeLoggerFactory.get(DialogueNodeSelectionStrategy.class);
    private static final Splitter SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();

    static List<DialogueNodeSelectionStrategy> fromHeader(String header) {
        return ImmutableList.copyOf(
                Lists.transform(SPLITTER.splitToList(header), DialogueNodeSelectionStrategy::safeValueOf));
    }

    /**
     * We allow server-determined headers to access some incubating dialogue-specific strategies (e.g. BALANCED_RTT)
     * which users can't normally configure.
     */
    private static DialogueNodeSelectionStrategy safeValueOf(String string) {
        if ("PIN_UNTIL_ERROR".equalsIgnoreCase(string)) {
            return PIN_UNTIL_ERROR;
        }
        if ("PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE".equalsIgnoreCase(string)) {
            return PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE;
        }
        if ("BALANCED".equalsIgnoreCase(string)) {
            return BALANCED;
        }
        log.info("Received unknown selection strategy {}", SafeArg.of("strategy", string));
        return UNKNOWN;
    }

    static DialogueNodeSelectionStrategy of(NodeSelectionStrategy strategy) {
        switch (strategy) {
            case PIN_UNTIL_ERROR:
                return PIN_UNTIL_ERROR;
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE;
            case ROUND_ROBIN:
                return BALANCED;
        }
        throw new SafeIllegalStateException("Unknown node selection strategy", SafeArg.of("strategy", strategy));
    }
}
