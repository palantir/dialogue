/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.dialogue.serde;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link DefaultConjureRuntime} provides functionality required by generated handlers.
 */
public final class DefaultConjureRuntime implements ConjureRuntime {

    private final BodySerDe bodySerDe;

    private DefaultConjureRuntime(Builder builder) {
        this.bodySerDe = new ConjureBodySerDe(
                // TODO(rfink): The default thing here is a little odd
                builder.encodings.isEmpty() ? ImmutableList.of(Encodings.json(), Encodings.cbor()) : builder.encodings);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BodySerDe bodySerDe() {
        return bodySerDe;
    }

    @Override
    public PlainSerDe plainSerDe() {
        return ConjurePlainSerDe.INSTANCE;
    }

    @Override
    public Clients clients() {
        return DefaultClients.INSTANCE;
    }

    public static final class Builder {

        private final List<Encoding> encodings = new ArrayList<>();

        private Builder() {}

        @CanIgnoreReturnValue
        public Builder encodings(Encoding value) {
            encodings.add(Preconditions.checkNotNull(value, "Value is required"));
            return this;
        }

        public DefaultConjureRuntime build() {
            return new DefaultConjureRuntime(this);
        }
    }
}
