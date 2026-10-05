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

import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import org.jspecify.annotations.Nullable;

/** Detects the retries-exhausted signal on a failure the way a server would before setting the header. */
final class RetriesExhaustedDetection {
    private static final int MAX_CAUSE_DEPTH = 100;

    private RetriesExhaustedDetection() {}

    static boolean isRetriesExhausted(Throwable failure) {
        @Nullable Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed instanceof SafeLoggable loggable && hasRetriesExhaustedArg(loggable)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasRetriesExhaustedArg(SafeLoggable loggable) {
        for (Arg<?> arg : loggable.getArgs()) {
            if (Responses.RETRIES_EXHAUSTED.equals(arg.getName())
                    && "true".equalsIgnoreCase(String.valueOf(arg.getValue()))) {
                return true;
            }
        }
        return false;
    }
}
