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

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;

public final class HttpPathParser {

    private static final Splitter SPLITTER =
            Splitter.on('/').omitEmptyStrings(); // omit empty segments; typically the first segment is empty

    private final ResolverContext context;

    public HttpPathParser(ResolverContext context) {
        this.context = context;
    }

    public Optional<HttpPath> getHttpPath(Element element, AnnotationReflector requestAnnotation) {
        try {
            String path = requestAnnotation.getFieldMaybe("path", String.class).orElseThrow();

            List<HttpPathSegment> pathSegments = SPLITTER.splitToStream(path)
                    .map(HttpPathParser::toHttpPathSegment)
                    .collect(Collectors.toList());

            return Optional.of(ImmutableHttpPath.of(pathSegments));
        } catch (IllegalArgumentException e) {
            context.reportError("Failed to parse http path", element, e);
            return Optional.empty();
        }
    }

    private static HttpPathSegment toHttpPathSegment(String pathSegment) {
        if (pathSegment.startsWith("{") && pathSegment.endsWith("}")) {
            return HttpPathSegments.variable(pathSegment.substring(1, pathSegment.length() - 1));
        } else {
            return HttpPathSegments.fixed(pathSegment);
        }
    }
}
