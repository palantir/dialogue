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
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.SafeLoggable;
import java.util.Map;
import org.junit.Test;

public final class PathTemplateTest {

    private static final ImmutableMap<String, String> A = ImmutableMap.of("a", "A");
    private static final ImmutableMap<String, String> A_B = ImmutableMap.of("a", "A", "b", "B");
    private static final ImmutableMap<String, String> A_B_C = ImmutableMap.of("a", "A", "b", "B", "c", "C");
    private static final ImmutableMap<String, String> B_C = ImmutableMap.of("b", "B", "c", "C");

    @Test
    public void testEmptyPath() {
        PathTemplate template = PathTemplate.builder().build();
        assertThat(fill(template, ImmutableMap.of())).isEmpty();
    }

    @Test
    public void testNoParameters() {
        PathTemplate template = PathTemplate.builder().fixed("a").fixed("b").build();
        assertThat(fill(template, ImmutableMap.of())).isEqualTo("/a/b");
    }

    @Test
    public void testVariableSegments() {
        PathTemplate template =
                PathTemplate.builder().variable("a").variable("b").build();
        assertThat(fill(template, A_B)).isEqualTo("/A/B");
    }

    @Test
    public void testFixedAndVariableSegments() {
        PathTemplate template = PathTemplate.builder()
                .fixed("a")
                .variable("b")
                .variable("c")
                .fixed("d")
                .build();
        assertThat(fill(template, B_C)).isEqualTo("/a/B/C/d");
    }

    @Test
    public void testTooFewParameters() {
        PathTemplate template =
                PathTemplate.builder().variable("a").variable("b").build();
        assertThatThrownBy(() -> fill(template, A))
                .isInstanceOf(IllegalArgumentException.class)
                .isInstanceOf(SafeLoggable.class)
                .hasMessage("Provided parameter map does not contain segment variable name: {variable=b}");
    }

    @Test
    public void testTooManyParameters() {
        PathTemplate template =
                PathTemplate.builder().variable("a").variable("b").build();
        assertThatThrownBy(() -> fill(template, A_B_C))
                .isInstanceOf(VerifyException.class)
                .hasMessage("Too many parameters supplied, this is a bug");
    }

    private static String fill(PathTemplate template, Map<String, String> params) {
        UrlBuilder url = UrlBuilder.http().host("unused").port(1);
        template.fill(params, url);
        return url.build().getPath();
    }
}
