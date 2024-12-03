/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Preconditions;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

public final class DeserializerArgs<T> {
    private final TypeMarker<T> baseType;
    private final TypeMarker<? extends T> expectedResultType;
    private final ImmutableMap<String, TypeMarker<? extends T>> errorNameToTypeMarker;

    private DeserializerArgs(
            TypeMarker<T> baseType,
            TypeMarker<? extends T> expectedResultType,
            ImmutableMap<String, TypeMarker<? extends T>> map) {
        this.baseType = baseType;
        this.expectedResultType = expectedResultType;
        this.errorNameToTypeMarker = map;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private boolean buildInvoked = false;
        private TypeMarker<T> baseType;
        private boolean baseTypeSet = false;
        private TypeMarker<? extends T> expectedResultType;
        private boolean expectedResultSet = false;
        private final Map<String, TypeMarker<? extends T>> errorNameToTypeMarker;

        @SuppressWarnings("NullAway")
        // We ensure that the baseType and expectedResultType are set before building.
        private Builder() {
            this.errorNameToTypeMarker = new HashMap<>();
        }

        public Builder<T> withBaseType(@Nonnull TypeMarker<T> base) {
            checkNotBuilt();
            this.baseType = Preconditions.checkNotNull(base, "base type must be non-null");
            this.baseTypeSet = true;
            return this;
        }

        public Builder<T> withExpectedResult(TypeMarker<? extends T> expectedResultT) {
            checkNotBuilt();
            this.expectedResultType =
                    Preconditions.checkNotNull(expectedResultT, "expected result type must be non-null");
            this.expectedResultSet = true;
            return this;
        }

        public Builder<T> withErrorType(@Nonnull String errorName, @Nonnull TypeMarker<? extends T> errorType) {
            checkNotBuilt();
            this.errorNameToTypeMarker.put(
                    Preconditions.checkNotNull(errorName, "error name must be non-null"),
                    Preconditions.checkNotNull(errorType, "error type must be non-null"));
            return this;
        }

        public DeserializerArgs<T> build() {
            checkNotBuilt();
            checkRequiredArgsSet();
            buildInvoked = true;
            return new DeserializerArgs<>(baseType, expectedResultType, ImmutableMap.copyOf(errorNameToTypeMarker));
        }

        private void checkNotBuilt() {
            Preconditions.checkState(!buildInvoked, "Build has already been called");
        }

        private void checkRequiredArgsSet() {
            Preconditions.checkState(baseTypeSet, "base type must be set");
            Preconditions.checkState(expectedResultSet, "expected result type must be set");
        }
    }

    public TypeMarker<? extends T> baseType() {
        return baseType;
    }

    public TypeMarker<? extends T> expectedResultType() {
        return expectedResultType;
    }

    public Map<String, TypeMarker<? extends T>> errorNameToTypeMarker() {
        return errorNameToTypeMarker;
    }
}
