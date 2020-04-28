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
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.List;

public enum DialogueNodeSelectionStrategy {
    PIN_UNTIL_ERROR,
    PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE,
    BALANCED,
    UNKNOWN;

    static List<DialogueNodeSelectionStrategy> fromHeader(String header) {
        return Splitter.on(";").splitToList(header).stream()
                .map(DialogueNodeSelectionStrategy::safeValueOf)
                .collect(ImmutableList.toImmutableList());
    }

    private static DialogueNodeSelectionStrategy safeValueOf(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return UNKNOWN;
        }
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
