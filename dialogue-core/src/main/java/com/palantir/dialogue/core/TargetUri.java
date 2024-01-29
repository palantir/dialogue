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

package com.palantir.dialogue.core;

import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.logsafe.Preconditions;
import java.net.InetAddress;
import java.util.Optional;
import javax.annotation.Nullable;

public final class TargetUri {

    private final String uri;
    private final Optional<InetAddress> resolvedAddress;

    private TargetUri(String uri, Optional<InetAddress> resolvedAddress) {
        this.uri = uri;
        this.resolvedAddress = resolvedAddress;
    }

    /** Original service URI. */
    public String uri() {
        return uri;
    }

    /** Resolved IP address of the {@link #uri()}, or the IP address from the URI if it is not a hostname. */
    public Optional<InetAddress> resolvedAddress() {
        return resolvedAddress;
    }

    @Override
    public String toString() {
        return "TargetUri{uri='" + uri + "', resolvedAddress=" + resolvedAddress + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        @Nullable
        private String uri;

        @Nullable
        private InetAddress resolvedAddress;

        private Builder() {}

        public Builder uri(String value) {
            this.uri = Preconditions.checkNotNull(value, "uri");
            return this;
        }

        public Builder resolvedAddress(InetAddress value) {
            this.resolvedAddress = Preconditions.checkNotNull(value, "resolvedAddress");
            return this;
        }

        @CheckReturnValue
        public TargetUri build() {
            return new TargetUri(Preconditions.checkNotNull(uri, "uri"), Optional.ofNullable(resolvedAddress));
        }
    }
}
