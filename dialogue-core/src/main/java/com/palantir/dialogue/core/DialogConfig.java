/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.conjure.java.client.config.ClientConfiguration;

/** Configuration specifying everything necessasry to talk to an upstream comprised of n nodes. */
public final class DialogConfig {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        /** this method exists for backcompat reasons. */
        public Builder from(ClientConfiguration cjrClientConfig) {
            //TODO
            return this;
        }

        public DialogConfig build() {
            return new DialogConfig();
        }
    }
}
