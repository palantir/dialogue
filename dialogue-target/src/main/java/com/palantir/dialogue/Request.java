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

import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** Defines the parameters of a single {@link Call} to an {@link Endpoint}. */
@DialogueImmutablesStyle
@Value.Immutable
public interface Request {
    /** The HTTP headers for this request, encoded as a map of {@code header-name: header-value}. */
    Map<String, String> headerParams();

    /**
     * The URL query parameters headers for this request. These should *not* be URL-encoded and {@link Channel}s are
     * responsible for performing the appropriate encoding if necessary.
     */
    Multimap<String, String> queryParams();

    /**
     * The HTTP path parameters for this request, encoded as a map of {@code param-name: param-value}. There is a
     * one-to-one correspondence between variable {@link PathTemplate.Segment path segments} of a {@link PathTemplate}
     * and the request's {@link #pathParams}.
     */
    Map<String, String> pathParams();

    /** The HTTP request body for this request or empty if this request does not contain a body. */
    Optional<RequestBody> body();

    static Builder builder() {
        return new Builder();
    }

    class Builder extends ImmutableRequest.Builder {}
}
