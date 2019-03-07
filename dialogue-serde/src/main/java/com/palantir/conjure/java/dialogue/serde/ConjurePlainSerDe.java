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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Package private internal API. */
enum ConjurePlainSerDe implements PlainSerDe {
    INSTANCE;

    @Override
    public BearerToken deserializeBearerToken(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return BearerToken.valueOf(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize bearertoken", ex);
        }
    }

    @Override
    public BearerToken deserializeBearerToken(@Nullable Iterable<String> in) {
        // BearerToken values should never be logged
        return deserializeBearerToken(getOnlyElementDoNotLogValues(in));
    }

    @Override
    public Optional<BearerToken> deserializeOptionalBearerToken(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeBearerToken(in));
    }

    @Override
    public Optional<BearerToken> deserializeOptionalBearerToken(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        // BearerToken values should never be logged
        return Optional.of(deserializeBearerToken(getOnlyElementDoNotLogValues(in)));
    }

    @Override
    public List<BearerToken> deserializeBearerTokenList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<BearerToken> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeBearerToken(item));
        }
        return builder.build();
    }

    @Override
    public Set<BearerToken> deserializeBearerTokenSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<BearerToken> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeBearerToken(item));
        }
        return builder.build();
    }

    @Override
    public boolean deserializeBoolean(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return Boolean.parseBoolean(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize boolean", ex);
        }
    }

    @Override
    public boolean deserializeBoolean(@Nullable Iterable<String> in) {
        return deserializeBoolean(getOnlyElement(in));
    }

    @Override
    public Optional<Boolean> deserializeOptionalBoolean(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeBoolean(in));
    }

    @Override
    public Optional<Boolean> deserializeOptionalBoolean(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        return Optional.of(deserializeBoolean(getOnlyElement(in)));
    }

    @Override
    public List<Boolean> deserializeBooleanList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<Boolean> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeBoolean(item));
        }
        return builder.build();
    }

    @Override
    public Set<Boolean> deserializeBooleanSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<Boolean> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeBoolean(item));
        }
        return builder.build();
    }

    @Override
    public OffsetDateTime deserializeDateTime(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return OffsetDateTime.parse(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize datetime", ex);
        }
    }

    @Override
    public OffsetDateTime deserializeDateTime(@Nullable Iterable<String> in) {
        return deserializeDateTime(getOnlyElement(in));
    }

    @Override
    public Optional<OffsetDateTime> deserializeOptionalDateTime(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeDateTime(in));
    }

    @Override
    public Optional<OffsetDateTime> deserializeOptionalDateTime(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        return Optional.of(deserializeDateTime(getOnlyElement(in)));
    }

    @Override
    public List<OffsetDateTime> deserializeDateTimeList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<OffsetDateTime> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeDateTime(item));
        }
        return builder.build();
    }

    @Override
    public Set<OffsetDateTime> deserializeDateTimeSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<OffsetDateTime> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeDateTime(item));
        }
        return builder.build();
    }

    @Override
    public double deserializeDouble(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return Double.parseDouble(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize double", ex);
        }
    }

    @Override
    public double deserializeDouble(@Nullable Iterable<String> in) {
        return deserializeDouble(getOnlyElement(in));
    }

    @Override
    public OptionalDouble deserializeOptionalDouble(@Nullable String in) {
        if (in == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(deserializeDouble(in));
    }

    @Override
    public OptionalDouble deserializeOptionalDouble(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(deserializeDouble(getOnlyElement(in)));
    }

    @Override
    public List<Double> deserializeDoubleList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<Double> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeDouble(item));
        }
        return builder.build();
    }

    @Override
    public Set<Double> deserializeDoubleSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<Double> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeDouble(item));
        }
        return builder.build();
    }

    @Override
    public int deserializeInteger(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return Integer.parseInt(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize integer", ex);
        }
    }

    @Override
    public int deserializeInteger(@Nullable Iterable<String> in) {
        return deserializeInteger(getOnlyElement(in));
    }

    @Override
    public OptionalInt deserializeOptionalInteger(@Nullable String in) {
        if (in == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(deserializeInteger(in));
    }

    @Override
    public OptionalInt deserializeOptionalInteger(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(deserializeInteger(getOnlyElement(in)));
    }

    @Override
    public List<Integer> deserializeIntegerList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<Integer> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeInteger(item));
        }
        return builder.build();
    }

    @Override
    public Set<Integer> deserializeIntegerSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeInteger(item));
        }
        return builder.build();
    }

    @Override
    public ResourceIdentifier deserializeRid(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return ResourceIdentifier.valueOf(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize rid", ex);
        }
    }

    @Override
    public ResourceIdentifier deserializeRid(@Nullable Iterable<String> in) {
        return deserializeRid(getOnlyElement(in));
    }

    @Override
    public Optional<ResourceIdentifier> deserializeOptionalRid(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeRid(in));
    }

    @Override
    public Optional<ResourceIdentifier> deserializeOptionalRid(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        return Optional.of(deserializeRid(getOnlyElement(in)));
    }

    @Override
    public List<ResourceIdentifier> deserializeRidList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<ResourceIdentifier> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeRid(item));
        }
        return builder.build();
    }

    @Override
    public Set<ResourceIdentifier> deserializeRidSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<ResourceIdentifier> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeRid(item));
        }
        return builder.build();
    }

    @Override
    public SafeLong deserializeSafeLong(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return SafeLong.valueOf(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize safelong", ex);
        }
    }

    @Override
    public SafeLong deserializeSafeLong(@Nullable Iterable<String> in) {
        return deserializeSafeLong(getOnlyElement(in));
    }

    @Override
    public Optional<SafeLong> deserializeOptionalSafeLong(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeSafeLong(in));
    }

    @Override
    public Optional<SafeLong> deserializeOptionalSafeLong(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        return Optional.of(deserializeSafeLong(getOnlyElement(in)));
    }

    @Override
    public List<SafeLong> deserializeSafeLongList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<SafeLong> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeSafeLong(item));
        }
        return builder.build();
    }

    @Override
    public Set<SafeLong> deserializeSafeLongSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<SafeLong> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeSafeLong(item));
        }
        return builder.build();
    }

    @Override
    public String deserializeString(@Nullable String in) {
        return checkArgumentNotNull(in);
    }

    @Override
    public String deserializeString(@Nullable Iterable<String> in) {
        return deserializeString(getOnlyElement(in));
    }

    @Override
    public Optional<String> deserializeOptionalString(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeString(in));
    }

    @Override
    public Optional<String> deserializeOptionalString(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        return Optional.of(deserializeString(getOnlyElement(in)));
    }

    @Override
    public List<String> deserializeStringList(@Nullable Iterable<String> in) {
        return in == null ? Collections.emptyList() : ImmutableList.copyOf(in);
    }

    @Override
    public Set<String> deserializeStringSet(@Nullable Iterable<String> in) {
        return in == null ? Collections.emptySet() : ImmutableSet.copyOf(in);
    }

    @Override
    public UUID deserializeUuid(@Nullable String in) {
        checkArgumentNotNull(in);
        try {
            return UUID.fromString(in);
        } catch (RuntimeException ex) {
            throw new SafeIllegalArgumentException("failed to deserialize uuid", ex);
        }
    }

    @Override
    public UUID deserializeUuid(@Nullable Iterable<String> in) {
        return deserializeUuid(getOnlyElement(in));
    }

    @Override
    public Optional<UUID> deserializeOptionalUuid(@Nullable String in) {
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(deserializeUuid(in));
    }

    @Override
    public Optional<UUID> deserializeOptionalUuid(@Nullable Iterable<String> in) {
        if (in == null || Iterables.isEmpty(in)) {
            return Optional.empty();
        }
        return Optional.of(deserializeUuid(getOnlyElement(in)));
    }

    @Override
    public List<UUID> deserializeUuidList(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<UUID> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeUuid(item));
        }
        return builder.build();
    }

    @Override
    public Set<UUID> deserializeUuidSet(@Nullable Iterable<String> in) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<UUID> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeUuid(item));
        }
        return builder.build();
    }

    @Override
    public <T> T deserializeComplex(@Nullable String in, Function<String, T> factory) {
        return factory.apply(deserializeString(checkArgumentNotNull(in)));
    }

    @Override
    public <T> T deserializeComplex(@Nullable Iterable<String> in, Function<String, T> factory) {
        return factory.apply(deserializeString(in));
    }

    @Override
    public <T> Optional<T> deserializeOptionalComplex(
            @Nullable Iterable<String> in, Function<String, T> factory) {
        return deserializeOptionalString(in).map(factory);
    }

    @Override
    public <T> List<T> deserializeComplexList(@Nullable Iterable<String> in, Function<String, T> factory) {
        if (in == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        for (String item : in) {
            builder.add(deserializeComplex(item, factory));
        }
        return builder.build();
    }

    @Override
    public <T> Set<T> deserializeComplexSet(@Nullable Iterable<String> in, Function<String, T> factory) {
        if (in == null) {
            return Collections.emptySet();
        }
        ImmutableSet.Builder<T> builder = ImmutableSet.builder();
        for (String item : in) {
            builder.add(deserializeComplex(item, factory));
        }
        return builder.build();
    }

    private static <T> T getOnlyElement(@Nullable Iterable<T> input) {
        return getOnlyElementInternal(input, true);
    }

    private static <T> T getOnlyElementDoNotLogValues(@Nullable Iterable<T> input) {
        return getOnlyElementInternal(input, false);
    }

    private static <T> T getOnlyElementInternal(@Nullable Iterable<T> input, boolean includeValues) {
        if (input == null) {
            throw new SafeIllegalArgumentException("Expected one element but received null");
        }
        Iterator<T> iterator = input.iterator();
        if (!iterator.hasNext()) {
            throw new SafeIllegalArgumentException("Expected one element but received none");
        }
        T first = iterator.next();
        if (!iterator.hasNext()) {
            return first;
        }
        int size = Iterables.size(input);
        if (includeValues) {
            throw new SafeIllegalArgumentException("Expected one element",
                    SafeArg.of("size", size), UnsafeArg.of("received", input));
        } else {
            throw new SafeIllegalArgumentException("Expected one element", SafeArg.of("size", size));
        }
    }

    /** Throws a SafeIllegalArgumentException rather than NPE in order to cause a 400 response. */
    @CanIgnoreReturnValue
    private static <T> T checkArgumentNotNull(@Nullable T input) {
        if (input == null) {
            throw new SafeIllegalArgumentException("Value is required");
        }
        return input;
    }
}
