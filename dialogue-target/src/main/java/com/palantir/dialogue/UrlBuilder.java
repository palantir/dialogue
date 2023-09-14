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

import java.util.Collection;

public interface UrlBuilder {

    /** URL-encodes the given path segment and adds it to the list of segments. */
    UrlBuilder pathSegment(String thePath);

    default UrlBuilder pathSegments(Collection<String> paths) {
        paths.forEach(this::pathSegment);
        return this;
    }

    /**
     * URL-encodes the given query parameter name and value and adds them to the list of query parameters. Note that
     * no guarantee is made regarding the ordering of query parameters in the resulting URL.
     */
    UrlBuilder queryParam(String name, String value);
}
