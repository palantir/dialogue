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

import com.palantir.conjure.java.client.config.NodeSelectionStrategy;

public enum DialogueNodeSelectionStrategy {
    PIN_UNTIL_ERROR,
    PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE,
    BALANCED,
    UNKNOWN;

    static DialogueNodeSelectionStrategy of(NodeSelectionStrategy strategy) {
        switch (strategy) {
            case PIN_UNTIL_ERROR:
                return PIN_UNTIL_ERROR;
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE;
            case ROUND_ROBIN:
                return BALANCED;
        }
        return UNKNOWN;
    }
}
