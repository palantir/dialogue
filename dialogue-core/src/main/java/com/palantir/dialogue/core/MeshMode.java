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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.List;

public enum MeshMode {
    DEFAULT_NO_MESH,
    USE_EXTERNAL_MESH;

    /**
     * This prefix may reconfigure several aspects of the client to work better in a world where requests are routed
     * through a service mesh like istio/envoy.
     */
    private static final String MESH_PREFIX = "mesh-";

    static MeshMode fromUris(List<String> uris, SafeArg<String> channelName) {
        long meshUris = uris.stream().filter(s -> s.startsWith(MESH_PREFIX)).count();
        long normalUris = uris.stream().filter(s -> !s.startsWith(MESH_PREFIX)).count();

        if (meshUris == 0) {
            return MeshMode.DEFAULT_NO_MESH;
        } else {
            if (meshUris != 1) {
                throw new SafeIllegalStateException(
                        "Not expecting multiple 'mesh-' prefixed uris - please double-check the uris",
                        SafeArg.of("meshUris", meshUris),
                        SafeArg.of("normalUris", normalUris),
                        channelName);
            }
            if (normalUris != 0) {
                throw new SafeIllegalStateException(
                        "When a 'mesh-' prefixed uri is present, there should not be any normal uris - please double "
                                + "check the uris",
                        SafeArg.of("meshUris", meshUris),
                        SafeArg.of("normalUris", normalUris),
                        channelName);
            }

            return MeshMode.USE_EXTERNAL_MESH;
        }
    }

    public static String stripMeshPrefix(String input) {
        return input.startsWith(MESH_PREFIX) ? input.substring(MESH_PREFIX.length()) : input;
    }
}
