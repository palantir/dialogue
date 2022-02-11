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

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Immutable;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

@Immutable
public final class PathTemplate {

    // TODO(rfink): Add query parameters.
    private final ImmutableList<Segment> segments;

    private PathTemplate(Iterable<Segment> segments) {
        this.segments = ImmutableList.copyOf(segments);
        Set<String> seenVariables = Sets.newHashSetWithExpectedSize(this.segments.size());
        for (Segment segment : segments) {
            if (segment.variable != null) {
                boolean seen = seenVariables.add(segment.variable);
                Preconditions.checkArgument(
                        seen, "Duplicate segment variable names not allowed", SafeArg.of("variable", segment.variable));
            }
        }
    }

    public static PathTemplateBuilder builder() {
        return new PathTemplateBuilder();
    }

    // TODO(rfink): This doesn't encode parameters

    /** Populates this template with the given named parameters. */
    public void fill(Map<String, String> parameters, UrlBuilder url) {
        int numVariableSegments = 0;
        for (Segment segment : segments) {
            if (segment.fixed != null) {
                url.pathSegment(segment.fixed);
            } else {
                String variableSegment = parameters.get(segment.variable);
                if (variableSegment == null) {
                    throw new SafeNullPointerException(
                            "Provided parameter map does not contain segment variable name",
                            SafeArg.of("variable", segment.variable));
                }
                url.pathSegment(variableSegment);
                numVariableSegments += 1;
            }
        }
        Verify.verify(numVariableSegments == parameters.size(), "Too many parameters supplied, this is a bug");
    }

    /** Populates this template with the given named parameters. */
    public void fill(ListMultimap<String, String> parameters, UrlBuilder url) {
        for (Segment segment : segments) {
            if (segment.fixed != null) {
                url.pathSegment(segment.fixed);
            } else {
                parameters.get(segment.variable).forEach(url::pathSegment);
            }
        }
    }

    public static final class PathTemplateBuilder {
        private final List<Segment> segments = new ArrayList<>();

        public PathTemplateBuilder fixed(String fixed) {
            segments.add(Segment.fixed(fixed));
            return this;
        }

        public PathTemplateBuilder variable(String variable) {
            segments.add(Segment.variable(variable));
            return this;
        }

        public PathTemplate build() {
            return new PathTemplate(segments);
        }
    }

    @Immutable
    public static final class Segment {
        @Nullable
        private final String fixed;

        @Nullable
        private final String variable;

        private Segment(@Nullable String fixed, @Nullable String variable) {
            this.fixed = fixed;
            this.variable = variable;
        }

        public static Segment fixed(String fixed) {
            return new Segment(fixed, null);
        }

        public static Segment variable(String variable) {
            return new Segment(null, variable);
        }
    }
}
