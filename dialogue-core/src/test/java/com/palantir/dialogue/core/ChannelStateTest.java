/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;

class ChannelStateTest {
    private static final String STRING_VALUE = "hello";
    private static final OuterType COMPLEX_VALUE = ImmutableOuterType.builder()
            .putValues(
                    "foo",
                    ImmutableInnerType.builder()
                            .foo("hello")
                            .addBar(1)
                            .addBar(2)
                            .addBar(3)
                            .build())
            .putValues(
                    "bar",
                    ImmutableInnerType.builder()
                            .foo("world")
                            .addBar(4)
                            .addBar(5)
                            .addBar(6)
                            .build())
            .build();

    private static String createStringValue() {
        return STRING_VALUE;
    }

    private static OuterType createComplexValue() {
        return COMPLEX_VALUE;
    }

    @Test
    public void invokes_factory_when_retrieving_state() {
        ChannelState state = new ChannelState();
        ChannelState.Key<String> key = new ChannelState.Key<>(String.class, ChannelStateTest::createStringValue);
        assertThat(state.getState(key)).isEqualTo(STRING_VALUE);
    }

    @Test
    public void can_store_state_for_multiple_key_types() {
        ChannelState state = new ChannelState();
        ChannelState.Key<String> key1 = new ChannelState.Key<>(String.class, ChannelStateTest::createStringValue);
        ChannelState.Key<OuterType> key2 =
                new ChannelState.Key<>(OuterType.class, ChannelStateTest::createComplexValue);

        assertThat(state.getState(key1)).isEqualTo(STRING_VALUE);
        assertThat(state.getState(key2)).isEqualTo(COMPLEX_VALUE);
    }

    @Test
    public void retrieves_existing_state_without_invoking_factory() {
        ChannelState state = new ChannelState();
        ChannelState.Key<UUID> key = new ChannelState.Key<>(UUID.class, UUID::randomUUID);

        UUID stored = state.getState(key);
        assertThat(state.getState(key)).isEqualTo(stored);
    }

    @Test
    public void throws_when_factory_produces_null_value() {
        ChannelState state = new ChannelState();
        ChannelState.Key<String> key = new ChannelState.Key<>(String.class, () -> null);

        assertThatThrownBy(() -> state.getState(key)).isInstanceOf(Exception.class);
    }

    @Value.Immutable
    interface InnerType {
        String foo();

        List<Integer> bar();
    }

    @Value.Immutable
    interface OuterType {
        Map<String, InnerType> values();
    }
}