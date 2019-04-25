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

package com.palantir.dialogue.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.ri.ResourceIdentifier;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

// Example of the interface code conjure would generate for a simple SampleService.
public interface SampleService {

    final class SampleObject {
        @JsonProperty("intProperty")
        private final int intProperty;

        @JsonCreator
        public SampleObject(@JsonProperty("intProperty") int intProperty) {
            this.intProperty = intProperty;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            SampleObject that = (SampleObject) other;
            return intProperty == that.intProperty;
        }

        @Override
        public int hashCode() {
            return Objects.hash(intProperty);
        }
    }

    SampleObject objectToObject(String path, OffsetDateTime header, List<ResourceIdentifier> query, SampleObject body);
    void voidToVoid();
}
