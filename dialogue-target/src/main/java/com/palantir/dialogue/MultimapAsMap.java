/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Map;

/**
 * Utility functionality to transform a multimap into a standard map assuming all keys have at most one value.
 */
final class MultimapAsMap {

    static <K, V> Map<K, V> of(Multimap<K, V> multimap) {
        return Maps.transformValues(multimap.asMap(), values -> {
            int size = values.size();
            if (size <= 1) {
                return Iterables.getOnlyElement(values, null);
            }
            // It's important that we fail in this case to avoid building malformed requests.
            // Older clients cannot produce this kind of data, so the most likely scenario is
            // a client is attempting to add multiple path parameters but has implemented the
            // wrong Endpoint.renderPath method.
            throw new SafeIllegalStateException(
                    "Multiple values are not allowed, use the multimap accessor instead",
                    SafeArg.of("size", size),
                    UnsafeArg.of("values", values));
        });
    }

    private MultimapAsMap() {}
}
