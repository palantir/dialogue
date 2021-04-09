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

package com.palantir.dialogue.annotations.processor.data;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class InstanceVariables {

    private InstanceVariables() {}

    static String joinCamelCase(String... segments) {
        return Joiner.on("")
                .join(IntStream.range(0, segments.length)
                        .mapToObj(i -> {
                            String segment = segments[i];
                            CaseFormat caseFormat =
                                    CaseFormats.estimate(segment).get();
                            if (i == 0) {
                                return caseFormat.to(CaseFormat.LOWER_CAMEL, segment);
                            }
                            return caseFormat.to(CaseFormat.UPPER_CAMEL, segment);
                        })
                        .collect(Collectors.toList()));
    }
}
