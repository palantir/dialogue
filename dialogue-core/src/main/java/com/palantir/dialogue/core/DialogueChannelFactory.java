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

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.DialogueImmutablesStyle;
import java.net.InetAddress;
import java.util.Optional;
import java.util.OptionalInt;
import org.immutables.value.Value;

public interface DialogueChannelFactory {

    Channel create(ChannelArgs args);

    @Value.Immutable
    @DialogueImmutablesStyle
    interface ChannelArgs {
        String uri();

        Optional<InetAddress> resolvedAddress();

        OptionalInt uriIndexForInstrumentation();

        static Builder builder() {
            return new Builder();
        }

        class Builder extends ImmutableChannelArgs.Builder {}
    }

    @SuppressWarnings("deprecation")
    static DialogueChannelFactory from(ChannelFactory legacy) {
        return args -> legacy.create(args.uri());
    }
}
