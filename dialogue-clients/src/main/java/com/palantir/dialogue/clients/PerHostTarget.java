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

package com.palantir.dialogue.clients;

import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.logsafe.Preconditions;
import javax.annotation.Nullable;

public final class PerHostTarget {

    private final TargetUri targetUri;

    private PerHostTarget(TargetUri targetUri) {
        this.targetUri = targetUri;
    }

    public TargetUri targetUri() {
        return targetUri;
    }

    @Override
    public String toString() {
        return "Target{targetUri='" + targetUri + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PerHostTarget perHostTarget = (PerHostTarget) other;
        return targetUri.equals(perHostTarget.targetUri);
    }

    @Override
    public int hashCode() {
        return targetUri.hashCode();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        @Nullable
        private TargetUri targetUri;

        private Builder() {}

        public Builder targetUri(TargetUri value) {
            this.targetUri = Preconditions.checkNotNull(value, "targetUri");
            return this;
        }

        @CheckReturnValue
        public PerHostTarget build() {
            return new PerHostTarget(Preconditions.checkNotNull(targetUri, "targetUri"));
        }
    }
}
