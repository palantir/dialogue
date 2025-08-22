/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.conjure.java.api.errors.AbstractSerializableError;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.logsafe.Preconditions;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Arguments for deserializing exceptions from error responses.
 * <p>
 * This class enables deserializing error responses into exceptions. It holds the return type
 * for successful responses and maintains a mapping from error names to their corresponding error and exception types.
 *
 * @param <T> the return type for successful responses
 */
public final class ExceptionDeserializerArgs<T> {
    private final TypeMarker<T> returnType;
    private final ImmutableMap<String, ErrorExceptionPair<?>> errorNameToExceptionTypeMarkers;

    private ExceptionDeserializerArgs(
            TypeMarker<T> returnType, ImmutableMap<String, ErrorExceptionPair<?>> errorNameToExceptionTypeMarkers) {
        this.returnType = returnType;
        this.errorNameToExceptionTypeMarkers = errorNameToExceptionTypeMarkers;
    }

    // toString, equals, and hashCode are implemented because ExceptionDeserializerArgs are used as keys in a Caffeine
    // cache.
    @Override
    public String toString() {
        return "ExceptionDeserializerArgs{"
                + "returnType=" + returnType
                + ", errorNameToExceptionTypeMarkers=" + errorNameToExceptionTypeMarkers
                + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ExceptionDeserializerArgs<?> other)) {
            return false;
        }
        return Objects.equals(returnType, other.returnType)
                && Objects.equals(errorNameToExceptionTypeMarkers, other.errorNameToExceptionTypeMarkers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnType, errorNameToExceptionTypeMarkers);
    }

    // A builder is manually constructed (instead of using a library like Immutables) to avoid having to force all
    // clients to take a dependency on such a library.
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private boolean buildInvoked = false;
        private @Nullable TypeMarker<T> returnType;
        private final ImmutableMap.Builder<String, ErrorExceptionPair<?>> errorNameToExceptionTypeMarkers;

        private Builder() {
            this.errorNameToExceptionTypeMarkers = ImmutableMap.builder();
        }

        public Builder<T> returnType(TypeMarker<T> returnT) {
            checkNotBuilt();
            this.returnType = Preconditions.checkNotNull(returnT, "base type must be non-null");
            return this;
        }

        /**
         * Registers an error name with its corresponding error type and exception type.
         * @param errorName the name of the error
         * @param errorType the type marker for the error
         * @param exceptionType the type marker for the exception. It is expected that the exception type implements a
         * constructor that takes the error type as the first parameter, and an integer status code as the second.
         */
        public <U extends AbstractSerializableError<?>, V extends RemoteException> Builder<T> exception(
                String errorName, TypeMarker<U> errorType, TypeMarker<V> exceptionType) {
            checkNotBuilt();
            this.errorNameToExceptionTypeMarkers.put(
                    Preconditions.checkNotNull(errorName, "error name must be non-null"),
                    new ErrorExceptionPair<>(
                            Preconditions.checkNotNull(errorType, "error type must be non-null"),
                            Preconditions.checkNotNull(exceptionType, "exception type must be non-null")));
            return this;
        }

        public ExceptionDeserializerArgs<T> build() {
            checkNotBuilt();
            Preconditions.checkNotNull(returnType, "base type must be set");
            buildInvoked = true;
            return new ExceptionDeserializerArgs<>(returnType, errorNameToExceptionTypeMarkers.buildKeepingLast());
        }

        private void checkNotBuilt() {
            Preconditions.checkState(!buildInvoked, "build has already been called");
        }
    }

    public TypeMarker<T> returnType() {
        return returnType;
    }

    public ImmutableMap<String, ErrorExceptionPair<?>> errorNameToExceptionTypeMarkers() {
        return errorNameToExceptionTypeMarkers;
    }

    public record ErrorExceptionPair<U extends AbstractSerializableError<?>>(
            TypeMarker<U> errorType, TypeMarker<? extends RemoteException> exceptionType) {}
}
