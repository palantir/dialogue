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

import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.core.Config.MeshMode;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void normal_uris_are_not_mesh() {
        Config cf = makeConfig("https://server.whatever", "http://server.another");
        assertThat(cf.mesh()).isEqualTo(MeshMode.DEFAULT_NO_MESH);
    }

    @Test
    void mixed_uris_leaves_mesh_mode_off() {
        Config cf = makeConfig("https://server.whatever", "mesh-http://server.another");
        assertThat(cf.mesh()).isEqualTo(MeshMode.DEFAULT_NO_MESH);
    }

    @Test
    void single_prefix_triggers_mesh_mode() {
        Config cf = makeConfig("mesh-https://server.whatever");
        assertThat(cf.mesh()).isEqualTo(MeshMode.USE_EXTERNAL_MESH);
    }

    @Test
    void multiple_prefixes_trigger_mesh_mode() {
        Config cf = makeConfig("mesh-https://server.whatever", "mesh-http://server.another");
        assertThat(cf.mesh()).isEqualTo(MeshMode.USE_EXTERNAL_MESH);
    }

    @Test
    void empty_uris_doesnt_break() {
        Config cf = makeConfig();
        assertThat(cf.mesh()).isEqualTo(MeshMode.DEFAULT_NO_MESH);
    }

    private ImmutableConfig makeConfig(String... uris) {
        return ImmutableConfig.builder()
                .channelName("channelName")
                .channelFactory(_uri -> {
                    throw new UnsupportedOperationException("not implemented");
                })
                .rawConfig(TestConfigurations.create(uris))
                .build();
    }
}
