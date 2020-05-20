/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.junit.jupiter.api.Test;

class StringMultimapTest {

    ListMultimap<String, String> reference = guavaReference();

    private ListMultimap<String, String> guavaReference() {
        ListMultimap<String, String> mutable =
                MultimapBuilder.linkedHashKeys().arrayListValues().build();
        mutable.put("key1", "hello");
        mutable.put("key1", "world");
        mutable.put("key2", "hello");
        mutable.put("key2", "my");
        mutable.put("key2", "favou|rite");
        mutable.put("key2", "world");
        return mutable;
    }

    @Test
    void basic_comparison() {
        StringMultimap map = new StringMultimap(ImmutableMap.<String, String>builder()
                .put("key1", "hello|world")
                .put("key2", "hello|my|favou^|rite|world")
                .build());
        test(map);
    }

    @Test
    void builder() {
        StringMultimap map2 = StringMultimap.linkedHashMapBuilder()
                .putAll("key1", "hello")
                .putAll("key1", "world")
                .putAll("key2", "hello", "my", "favou|rite", "world")
                .build();
        test(map2);
    }

    @Test
    void tree() {
        StringMultimap map = StringMultimap.treeMapBuilder(String.CASE_INSENSITIVE_ORDER)
                .putAll("key4", "c", "b")
                .putAll("key1", "b", "a")
                .putAll("key2", "x")
                .build();
        assertThat(map.keySet()).containsExactly("key1", "key2", "key4");
    }

    private void test(StringMultimap candidate) {
        assertThat(candidate.size()).describedAs(candidate.toString()).isEqualTo(reference.size());
        assertThat(candidate.values()).containsExactlyElementsOf(reference.values());
        assertThat(candidate.entries()).containsExactlyElementsOf(reference.entries());
        assertThat(candidate.keys()).containsExactlyElementsOf(reference.keys());
        assertThat(candidate.keySet()).isEqualTo(reference.keySet());
        assertThat(candidate.get("key1")).isEqualTo(reference.get("key1"));
        assertThat(candidate.containsKey("key1")).isEqualTo(reference.containsKey("key1"));
        assertThat(candidate.containsValue("my")).isEqualTo(reference.containsValue("my"));
        assertThat(candidate.containsEntry("key1", "hello")).isEqualTo(reference.containsEntry("key1", "hello"));
    }
}
