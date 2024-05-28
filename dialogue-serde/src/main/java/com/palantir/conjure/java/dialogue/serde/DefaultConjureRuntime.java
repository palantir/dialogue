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

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.PlainSerDe;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link DefaultConjureRuntime} provides functionality required by generated handlers.
 */
public final class DefaultConjureRuntime implements ConjureRuntime {
    public static final CaffeineSpec DEFAULT_SERDE_CACHE_SPEC =
            CaffeineSpec.parse("maximumSize=1000,expireAfterAccess=1m,weakKeys,weakValues");

    static final ImmutableList<WeightedEncoding> DEFAULT_ENCODINGS = ImmutableList.of(
            WeightedEncoding.of(Encodings.json(), .9),
            WeightedEncoding.of(Encodings.smile(), 1),
            WeightedEncoding.of(Encodings.cbor(), .7));

    private final BodySerDe bodySerDe;

    private DefaultConjureRuntime(Builder builder) {
        this.bodySerDe = new ConjureBodySerDe(
                builder.encodings.isEmpty() ? DEFAULT_ENCODINGS : builder.encodings,
                ErrorDecoder.INSTANCE,
                Encodings.emptyContainerDeserializer(),
                DEFAULT_SERDE_CACHE_SPEC);
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

        private final List<WeightedEncoding> encodings = new ArrayList<>();

        private Builder() {}

        @CanIgnoreReturnValue
        public Builder encodings(Encoding value) {
            encodings.add(WeightedEncoding.of(value));
            return this;
        }

        /**
         * Register an {@link Encoding} with a weight value between zero and one (inclusive). The weight is used to
         * determine preference in content-type negotiation.
         */
        @CanIgnoreReturnValue
        public Builder encodings(Encoding value, double weight) {
            encodings.add(WeightedEncoding.of(value, weight));
            return this;
        }

        public DefaultConjureRuntime build() {
            return new DefaultConjureRuntime(this);
        }
    }
}
