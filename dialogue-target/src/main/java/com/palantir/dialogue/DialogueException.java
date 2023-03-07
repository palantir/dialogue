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

package com.palantir.dialogue;

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import java.util.List;
import javax.annotation.Nullable;

/** Internal marker type for Dialogue network layer failures where no response is returned. */
public final class DialogueException extends RuntimeException implements SafeLoggable {
    private static final String MESSAGE = "Network transport failure";

    public DialogueException(@Nullable Throwable cause) {
        super(MESSAGE, cause);
    }

    @Override
    public String getLogMessage() {
        return MESSAGE;
    }

    @Override
    public List<Arg<?>> getArgs() {
        return ImmutableList.of();
    }
}
