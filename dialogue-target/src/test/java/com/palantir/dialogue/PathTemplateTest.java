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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.SafeLoggable;
import java.util.List;
import org.junit.Test;

public final class PathTemplateTest {

    private static final ImmutableMap<String, String> A = ImmutableMap.of("a", "A");
    private static final ImmutableMap<String, String> A_B = ImmutableMap.of("a", "A", "b", "B");
    private static final ImmutableMap<String, String> A_B_C = ImmutableMap.of("a", "A", "b", "B", "c", "C");
    private static final ImmutableMap<String, String> B_C = ImmutableMap.of("b", "B", "c", "C");

    @Test
    public void testEmptyPath() throws Exception {
        assertThat(PathTemplate.of(list()).fill(ImmutableMap.of())).isEqualTo("/");
    }

    @Test
    public void testNoParameters() throws Exception {
        assertThat(PathTemplate.of(list(fixed("a"), fixed("b"))).fill(ImmutableMap.of())).isEqualTo("/a/b");
    }

    @Test
    public void testVariableSegments() throws Exception {
        assertThat(PathTemplate.of(list(variable("a"), variable("b"))).fill(A_B)).isEqualTo("/A/B");
    }

    @Test
    public void testFixedAndVariableSegments() throws Exception {
        assertThat(PathTemplate.of(list(fixed("a"), variable("b"), variable("c"), fixed("d"))).fill(B_C))
                .isEqualTo("/a/B/C/d");
    }

    @Test
    public void testTooFewParameters() throws Exception {
        assertThatThrownBy(() -> PathTemplate.of(list(variable("a"), variable("b"))).fill(A))
                .isInstanceOf(IllegalArgumentException.class)
                .isInstanceOf(SafeLoggable.class)
                .hasMessage("Provided parameter map does not contain segment variable name: {variable=b}");
    }

    @Test
    public void testTooManyParameters() throws Exception {
        assertThatThrownBy(() -> PathTemplate.of(list(variable("a"), variable("b"))).fill(A_B_C))
                .isInstanceOf(VerifyException.class)
                .hasMessage("Too many parameters supplied, this is a bug");
    }

    private PathTemplate.Segment fixed(String name) {
        return PathTemplate.Segment.fixed(name);
    }

    private PathTemplate.Segment variable(String variable) {
        return PathTemplate.Segment.variable(variable);
    }

    @SafeVarargs
    private static <T> List<T> list(T... objects) {
        return ImmutableList.copyOf(objects);
    }
}
