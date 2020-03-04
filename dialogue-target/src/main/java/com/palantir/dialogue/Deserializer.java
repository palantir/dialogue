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

import java.util.Optional;

/** Reads objects from a response. */
public interface Deserializer<T> {

    /** Deserializes the response body. */
    T deserialize(Response response);

    /**
     * Returns the content types this deserializer accepts, if any.
     * Values are structured for the <pre>Accept</pre> header based
     * on <a href="https://tools.ietf.org/html/rfc7231#section-5.3.2">rfc 7231 section 5.3.2</a>.
     */
    default Optional<String> accepts() {
        // TODO(ckozak): This should not be a functional interface. This method is default
        // only to allow the generator to be updated to avoid deserializer method references.
        return Optional.empty();
    }
}
