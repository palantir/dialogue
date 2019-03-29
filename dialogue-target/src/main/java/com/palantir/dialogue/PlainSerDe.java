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
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Provides functionality to parse supported types using the
 * <a href="https://palantir.github.io/conjure/#/docs/spec/wire?id=_6-plain-format">Conjure PLAIN format</a>.
 *
 * These utilities are used to parse HTTP path, query, and header parameter values.
 */
public interface PlainSerDe {

    // TODO(ckozak): Write javadoc

    BearerToken deserializeBearerToken(@Nullable String in);

    BearerToken deserializeBearerToken(@Nullable Iterable<String> in);

    Optional<BearerToken> deserializeOptionalBearerToken(@Nullable String in);

    Optional<BearerToken> deserializeOptionalBearerToken(@Nullable Iterable<String> in);

    List<BearerToken> deserializeBearerTokenList(@Nullable Iterable<String> in);

    Set<BearerToken> deserializeBearerTokenSet(@Nullable Iterable<String> in);

    boolean deserializeBoolean(@Nullable String in);

    boolean deserializeBoolean(@Nullable Iterable<String> in);

    Optional<Boolean> deserializeOptionalBoolean(@Nullable String in);

    Optional<Boolean> deserializeOptionalBoolean(@Nullable Iterable<String> in);

    List<Boolean> deserializeBooleanList(@Nullable Iterable<String> in);

    Set<Boolean> deserializeBooleanSet(@Nullable Iterable<String> in);

    OffsetDateTime deserializeDateTime(@Nullable String in);

    OffsetDateTime deserializeDateTime(@Nullable Iterable<String> in);

    Optional<OffsetDateTime> deserializeOptionalDateTime(@Nullable String in);

    Optional<OffsetDateTime> deserializeOptionalDateTime(@Nullable Iterable<String> in);

    List<OffsetDateTime> deserializeDateTimeList(@Nullable Iterable<String> in);

    Set<OffsetDateTime> deserializeDateTimeSet(@Nullable Iterable<String> in);

    double deserializeDouble(@Nullable String in);

    double deserializeDouble(@Nullable Iterable<String> in);

    OptionalDouble deserializeOptionalDouble(@Nullable String in);

    OptionalDouble deserializeOptionalDouble(@Nullable Iterable<String> in);

    List<Double> deserializeDoubleList(@Nullable Iterable<String> in);

    Set<Double> deserializeDoubleSet(@Nullable Iterable<String> in);

    int deserializeInteger(@Nullable String in);

    int deserializeInteger(@Nullable Iterable<String> in);

    OptionalInt deserializeOptionalInteger(@Nullable String in);

    OptionalInt deserializeOptionalInteger(@Nullable Iterable<String> in);

    List<Integer> deserializeIntegerList(@Nullable Iterable<String> in);

    Set<Integer> deserializeIntegerSet(@Nullable Iterable<String> in);

    ResourceIdentifier deserializeRid(String in);

    ResourceIdentifier deserializeRid(@Nullable Iterable<String> in);

    Optional<ResourceIdentifier> deserializeOptionalRid(@Nullable String in);

    Optional<ResourceIdentifier> deserializeOptionalRid(@Nullable Iterable<String> in);

    List<ResourceIdentifier> deserializeRidList(@Nullable Iterable<String> in);

    Set<ResourceIdentifier> deserializeRidSet(@Nullable Iterable<String> in);

    SafeLong deserializeSafeLong(@Nullable String in);

    SafeLong deserializeSafeLong(@Nullable Iterable<String> in);

    Optional<SafeLong> deserializeOptionalSafeLong(@Nullable String in);

    Optional<SafeLong> deserializeOptionalSafeLong(@Nullable Iterable<String> in);

    List<SafeLong> deserializeSafeLongList(@Nullable Iterable<String> in);

    Set<SafeLong> deserializeSafeLongSet(@Nullable Iterable<String> in);

    String deserializeString(@Nullable String in);

    String deserializeString(@Nullable Iterable<String> in);

    Optional<String> deserializeOptionalString(@Nullable String in);

    Optional<String> deserializeOptionalString(@Nullable Iterable<String> in);

    List<String> deserializeStringList(@Nullable Iterable<String> in);

    Set<String> deserializeStringSet(@Nullable Iterable<String> in);

    UUID deserializeUuid(@Nullable String in);

    UUID deserializeUuid(@Nullable Iterable<String> in);

    Optional<UUID> deserializeOptionalUuid(@Nullable String in);

    Optional<UUID> deserializeOptionalUuid(@Nullable Iterable<String> in);

    List<UUID> deserializeUuidList(@Nullable Iterable<String> in);

    Set<UUID> deserializeUuidSet(@Nullable Iterable<String> in);

    <T> T deserializeComplex(@Nullable String in, Function<String, T> factory);

    <T> T deserializeComplex(@Nullable Iterable<String> in, Function<String, T> factory);

    <T> Optional<T> deserializeOptionalComplex(@Nullable Iterable<String> in, Function<String, T> factory);

    <T> List<T> deserializeComplexList(@Nullable Iterable<String> in, Function<String, T> factory);

    <T> Set<T> deserializeComplexSet(@Nullable Iterable<String> in, Function<String, T> factory);

}
