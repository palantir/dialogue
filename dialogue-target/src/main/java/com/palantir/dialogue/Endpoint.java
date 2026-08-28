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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Defines a single HTTP-based RPC endpoint in terms of a {@link #renderPath path} and {@link #httpMethod HTTP method},
 * as well as the types of the request and response body.
 */
public interface Endpoint {
    /**
     * {@link #renderPath(ListMultimap, UrlBuilder)} is preferred, however at least one {@code renderPath} must be
     * implemented to avoid infinite recursion.
     */
    default void renderPath(Map<String, String> params, UrlBuilder url) {
        renderPath(
                params.isEmpty()
                        ? ImmutableListMultimap.of()
                        : ImmutableListMultimap.<String, String>builder()
                                .putAll(params.entrySet())
                                .build(),
                url);
    }

    default void renderPath(ListMultimap<String, String> params, UrlBuilder url) {
        renderPath(params.isEmpty() ? ImmutableMap.of() : MultimapAsMap.of(params), url);
    }

    HttpMethod httpMethod();

    String serviceName();

    String endpointName();

    String version();

    default Set<String> tags() {
        return Collections.emptySet();
    }
}
