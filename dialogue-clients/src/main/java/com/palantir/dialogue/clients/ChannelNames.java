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

package com.palantir.dialogue.clients;

final class ChannelNames {

    static String reloading(String serviceName) {
        return "dialogue-" + serviceName;
    }

    static String reloading(String serviceName, ImmutableReloadingParams params) {
        return reloading(serviceName) + summarizeOptions(params);
    }

    static String sticky(String serviceName, ImmutableReloadingParams params) {
        return "dialogue-sticky-" + serviceName + summarizeOptions(params);
    }

    static String nonReloading(Class<?> clazz, AugmentClientConfig augment) {
        return "dialogue-nonreloading-" + clazz.getSimpleName() + summarizeOptions(augment);
    }

    private static String summarizeOptions(AugmentClientConfig augment) {
        StringBuilder builder = new StringBuilder();
        augment.nodeSelectionStrategy().ifPresent(value -> builder.append("-").append(value));
        augment.maxNumRetries()
                .ifPresent(value -> builder.append("-MAX_RETRIES_").append(value));
        augment.clientQoS().ifPresent(value -> builder.append("-").append(value));
        augment.serverQoS().ifPresent(value -> builder.append("-").append(value));
        augment.retryOnTimeout().ifPresent(value -> builder.append("-").append(value));
        return builder.toString();
    }

    private ChannelNames() {}
}
