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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MeshModeTest {

    @Test
    void normal_uris_are_not_mesh() {
        MeshMode mode = fromUris("https://server.whatever", "http://server.another");
        assertThat(mode).isEqualTo(MeshMode.DEFAULT_NO_MESH);
    }

    @Test
    void single_prefix_triggers_mesh_mode() {
        MeshMode mode = fromUris("mesh-https://server.whatever");
        assertThat(mode).isEqualTo(MeshMode.USE_EXTERNAL_MESH);
    }

    @Test
    void empty_uris_doesnt_break() {
        MeshMode mode = fromUris();
        assertThat(mode).isEqualTo(MeshMode.DEFAULT_NO_MESH);
    }

    @Test
    void mixed_uris_breaks() {
        assertThatThrownBy(() -> fromUris("https://server.whatever", "mesh-http://server.another"))
                .isInstanceOf(SafeIllegalStateException.class)
                .hasMessage("Some uris have 'mesh-' prefix but others don't, please pick one or the other: "
                        + "{meshUris=1, normalUris=1, channelName=channelName}");
    }

    @Test
    void multiple_prefixes_breaks() {
        assertThatThrownBy(() -> fromUris("mesh-https://server.whatever", "mesh-http://server.another"))
                .isInstanceOf(SafeIllegalStateException.class)
                .hasMessage("Not expecting multiple 'mesh-' prefixed uris - please double-check the uris: "
                        + "{meshUris=2, normalUris=0, channelName=channelName}");
    }

    private MeshMode fromUris(String... uris) {
        return MeshMode.fromUris(
                Arrays.stream(uris).collect(Collectors.toList()), SafeArg.of("channelName", "channelName"));
    }
}
