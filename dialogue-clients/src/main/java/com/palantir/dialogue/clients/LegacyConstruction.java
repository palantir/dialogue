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

package com.palantir.dialogue.clients;

import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NoOpHostEventsSink;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.refreshable.Refreshable;

/** Utility functionality supporting client creation using the {@link ClientConfiguration} type. */
final class LegacyConstruction {
    private static final ReloadingFactory FACTORY = DialogueClients.create(
                    Refreshable.only(ServicesConfigBlock.empty()))
            .withHostEventsSink(NoOpHostEventsSink.INSTANCE);

    @SuppressWarnings("deprecation")
    static <T> T getNonReloading(Class<T> clientInterface, ClientConfiguration clientConfiguration) {
        return FACTORY.getNonReloading(clientInterface, clientConfiguration);
    }

    private LegacyConstruction() {}
}
