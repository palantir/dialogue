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

package com.palantir.dialogue;

import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Provides functionality for serializing supported types using the
 * <a href="https://palantir.github.io/conjure/#/docs/spec/wire?id=_6-plain-format">Conjure PLAIN format</a>.
 * <p>
 * These utilities are used to serialize HTTP path, query, and header parameter values.
 */
public interface PlainSerDe {

    String serializeBearerToken(BearerToken in);

    Optional<String> serializeOptionalBearerToken(Optional<BearerToken> in);

    List<String> serializeBearerTokenList(List<BearerToken> in);

    List<String> serializeBearerTokenSet(Set<BearerToken> in);

    String serializeBoolean(boolean in);

    Optional<String> serializeOptionalBoolean(Optional<Boolean> in);

    List<String> serializeBooleanList(List<Boolean> in);

    List<String> serializeBooleanSet(Set<Boolean> in);

    String serializeDateTime(OffsetDateTime in);

    Optional<String> serializeOptionalDateTime(Optional<OffsetDateTime> in);

    List<String> serializeDateTimeList(List<OffsetDateTime> in);

    List<String> serializeDateTimeSet(Set<OffsetDateTime> in);

    String serializeDouble(double in);

    Optional<String> serializeOptionalDouble(OptionalDouble in);

    List<String> serializeDoubleList(List<Double> in);

    List<String> serializeDoubleSet(Set<Double> in);

    String serializeInteger(int in);

    Optional<String> serializeOptionalInteger(OptionalInt in);

    List<String> serializeIntegerList(List<Integer> in);

    List<String> serializeIntegerSet(Set<Integer> in);

    String serializeRid(ResourceIdentifier in);

    Optional<String> serializeOptionalRid(Optional<ResourceIdentifier> in);

    List<String> serializeRidList(List<ResourceIdentifier> in);

    List<String> serializeRidSet(Set<ResourceIdentifier> in);

    String serializeSafeLong(SafeLong in);

    Optional<String> serializeOptionalSafeLong(Optional<SafeLong> in);

    List<String> serializeSafeLongList(List<SafeLong> in);

    List<String> serializeSafeLongSet(Set<SafeLong> in);

    String serializeString(String in);

    Optional<String> serializeOptionalString(Optional<String> in);

    List<String> serializeStringList(List<String> in);

    List<String> serializeStringSet(Set<String> in);

    String serializeUuid(UUID in);

    Optional<String> serializeOptionalUuid(Optional<UUID> in);

    List<String> serializeUuidList(List<UUID> in);

    List<String> serializeUuidSet(Set<UUID> in);
}
