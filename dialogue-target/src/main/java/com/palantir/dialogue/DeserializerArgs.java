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
import org.jspecify.annotations.Nullable;

/**
 * This class is used to specify the success and error types used to construct a {@link Deserializer}. A call to an
 * endpoint, when successful, returns a success type. When an error occurs, the response body is deserialized into an
 * error type. The base type is an interface that permits a success type and all error types.
 */
public final class DeserializerArgs<T> {
    private final TypeMarker<T> baseType;
    private final TypeMarker<? extends T> successType;
    private final ImmutableMap<String, TypeMarker<? extends T>> errorNameToTypeMarker;

    private DeserializerArgs(
            TypeMarker<T> baseType,
            TypeMarker<? extends T> successType,
            ImmutableMap<String, TypeMarker<? extends T>> map) {
        this.baseType = baseType;
        this.successType = successType;
        this.errorNameToTypeMarker = map;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private boolean buildInvoked = false;
        private @Nullable TypeMarker<T> baseType;
        private @Nullable TypeMarker<? extends T> successType;
        private final Map<String, TypeMarker<? extends T>> errorNameToTypeMarker;

        private Builder() {
            this.errorNameToTypeMarker = new HashMap<>();
        }

        public Builder<T> baseType(TypeMarker<T> baseT) {
            checkNotBuilt();
            this.baseType = Preconditions.checkNotNull(baseT, "base type must be non-null");
            return this;
        }

        public Builder<T> success(TypeMarker<? extends T> successT) {
            checkNotBuilt();
            this.successType = Preconditions.checkNotNull(successT, "success type must be non-null");
            return this;
        }

        public Builder<T> error(String errorName, TypeMarker<? extends T> errorT) {
            checkNotBuilt();
            this.errorNameToTypeMarker.put(
                    Preconditions.checkNotNull(errorName, "error name must be non-null"),
                    Preconditions.checkNotNull(errorT, "error type must be non-null"));
            return this;
        }

        public DeserializerArgs<T> build() {
            checkNotBuilt();
            Preconditions.checkNotNull(baseType, "base type must be set");
            Preconditions.checkNotNull(successType, "success type must be set");
            buildInvoked = true;
            return new DeserializerArgs<>(baseType, successType, ImmutableMap.copyOf(errorNameToTypeMarker));
        }

        private void checkNotBuilt() {
            Preconditions.checkState(!buildInvoked, "build has already been called");
        }
    }

    public TypeMarker<T> baseType() {
        return baseType;
    }

    public TypeMarker<? extends T> successType() {
        return successType;
    }

    public Map<String, TypeMarker<? extends T>> errorNameToTypeMarker() {
        return errorNameToTypeMarker;
    }
}
