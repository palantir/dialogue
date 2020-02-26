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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DialogueChannel implements Channel {
    private final Channel delegate;

    private DialogueChannel(Channel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.execute(endpoint, request);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Channel> channels = new ArrayList<>();
        private ClientConfiguration conf;

        @CheckReturnValue
        public Builder channels(Collection<Channel> channels) {
            channels.clear();
            channels.addAll(channels);
            return this;
        }

        @CheckReturnValue
        public Builder clientConfiguration(ClientConfiguration conf) {
            this.conf = conf;
            return this;
        }

        @CheckReturnValue
        public DialogueChannel build() {
            Preconditions.checkNotNull(conf, "ClientConfiguration is required");
            Preconditions.checkNotNull(channels, "Channels is required");

            return new DialogueChannel(Channels.create(channels, conf));
        }
    }
}
