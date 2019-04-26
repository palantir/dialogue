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

import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Package private internal API. */
enum ConjurePlainSerDe implements PlainSerDe {
    INSTANCE;

    @Override
    public String serializeBearerToken(BearerToken in) {
        return in.toString();
    }

    @Override
    public Optional<String> serializeOptionalBearerToken(Optional<BearerToken> in) {
        return in.map(this::serializeBearerToken);
    }

    @Override
    public List<String> serializeBearerTokenList(List<BearerToken> in) {
        return toList(in, this::serializeBearerToken);
    }

    @Override
    public List<String> serializeBearerTokenSet(Set<BearerToken> in) {
        return toList(in, this::serializeBearerToken);
    }

    @Override
    public String serializeBoolean(boolean in) {
        return in ? "true" : "false";
    }

    @Override
    public Optional<String> serializeOptionalBoolean(Optional<Boolean> in) {
        return in.map(this::serializeBoolean);
    }

    @Override
    public List<String> serializeBooleanList(List<Boolean> in) {
        return toList(in, this::serializeBoolean);
    }

    @Override
    public List<String> serializeBooleanSet(Set<Boolean> in) {
        return toList(in, this::serializeBoolean);
    }

    @Override
    public String serializeDateTime(OffsetDateTime in) {
        return in.toString();
    }

    @Override
    public Optional<String> serializeOptionalDateTime(Optional<OffsetDateTime> in) {
        return in.map(this::serializeDateTime);
    }

    @Override
    public List<String> serializeDateTimeList(List<OffsetDateTime> in) {
        return toList(in, this::serializeDateTime);
    }

    @Override
    public List<String> serializeDateTimeSet(Set<OffsetDateTime> in) {
        return toList(in, this::serializeDateTime);
    }

    @Override
    public String serializeDouble(double in) {
        return Double.toString(in);
    }

    @Override
    public Optional<String> serializeOptionalDouble(OptionalDouble in) {
        return in.isPresent() ? Optional.of(serializeDouble(in.getAsDouble())) : Optional.empty();
    }

    @Override
    public List<String> serializeDoubleList(List<Double> in) {
        return toList(in, this::serializeDouble);
    }

    @Override
    public List<String> serializeDoubleSet(Set<Double> in) {
        return toList(in, this::serializeDouble);
    }

    @Override
    public String serializeInteger(int in) {
        return Integer.toString(in);
    }

    @Override
    public Optional<String> serializeOptionalInteger(OptionalInt in) {
        return in.isPresent() ? Optional.of(serializeInteger(in.getAsInt())) : Optional.empty();
    }

    @Override
    public List<String> serializeIntegerList(List<Integer> in) {
        return toList(in, this::serializeInteger);
    }

    @Override
    public List<String> serializeIntegerSet(Set<Integer> in) {
        return toList(in, this::serializeInteger);
    }

    @Override
    public String serializeRid(ResourceIdentifier in) {
        return in.toString();
    }

    @Override
    public Optional<String> serializeOptionalRid(Optional<ResourceIdentifier> in) {
        return in.map(this::serializeRid);
    }

    @Override
    public List<String> serializeRidList(List<ResourceIdentifier> in) {
        return toList(in, this::serializeRid);
    }

    @Override
    public List<String> serializeRidSet(Set<ResourceIdentifier> in) {
        return toList(in, this::serializeRid);
    }

    @Override
    public String serializeSafeLong(SafeLong in) {
        return in.toString();
    }

    @Override
    public Optional<String> serializeOptionalSafeLong(Optional<SafeLong> in) {
        return in.map(this::serializeSafeLong);
    }

    @Override
    public List<String> serializeSafeLongList(List<SafeLong> in) {
        return toList(in, this::serializeSafeLong);
    }

    @Override
    public List<String> serializeSafeLongSet(Set<SafeLong> in) {
        return toList(in, this::serializeSafeLong);
    }

    @Override
    public String serializeString(String in) {
        return in;
    }

    @Override
    public Optional<String> serializeOptionalString(Optional<String> in) {
        return in;
    }

    @Override
    public List<String> serializeStringList(List<String> in) {
        return in;
    }

    @Override
    public List<String> serializeStringSet(Set<String> in) {
        return new ArrayList<>(in);
    }

    @Override
    public String serializeUuid(UUID in) {
        return in.toString();
    }

    @Override
    public Optional<String> serializeOptionalUuid(Optional<UUID> in) {
        return in.map(this::serializeUuid);
    }

    @Override
    public List<String> serializeUuidList(List<UUID> in) {
        return toList(in, this::serializeUuid);
    }

    @Override
    public List<String> serializeUuidSet(Set<UUID> in) {
        return toList(in, this::serializeUuid);
    }

    private static <T> List<String> toList(Collection<T> in, Function<T, String> toString) {
        List<String> out = new ArrayList<>(in.size());
        for (T i : in) {
            out.add(toString.apply(i));
        }
        return out;
    }
}
