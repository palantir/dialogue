/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.verification;

import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.com.palantir.conjure.verification.server.AutoDeserializeConfirmServiceBlocking;
import com.palantir.dialogue.com.palantir.conjure.verification.server.AutoDeserializeServiceBlocking;
import com.palantir.dialogue.com.palantir.conjure.verification.server.SingleHeaderServiceBlocking;
import com.palantir.dialogue.com.palantir.conjure.verification.server.SinglePathParamServiceBlocking;
import com.palantir.dialogue.com.palantir.conjure.verification.server.SingleQueryParamServiceBlocking;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;

public final class VerificationClients {
    private static final ConjureRuntime DIALOGUE_RUNTIME =
            DefaultConjureRuntime.builder().build();

    private VerificationClients() {}

    public static AutoDeserializeServiceBlocking autoDeserializeServiceJersey(VerificationServerRule server) {
        return AutoDeserializeServiceBlocking.of(
                ApacheHttpClientChannels.create(server.getClientConfiguration()), DIALOGUE_RUNTIME);
    }

    public static AutoDeserializeConfirmServiceBlocking confirmService(VerificationServerRule server) {
        return AutoDeserializeConfirmServiceBlocking.of(
                ApacheHttpClientChannels.create(server.getClientConfiguration()), DIALOGUE_RUNTIME);
    }

    public static SinglePathParamServiceBlocking singlePathParamService(VerificationServerRule server) {
        return SinglePathParamServiceBlocking.of(
                ApacheHttpClientChannels.create(server.getClientConfiguration()), DIALOGUE_RUNTIME);
    }

    public static SingleHeaderServiceBlocking singleHeaderService(VerificationServerRule server) {
        return SingleHeaderServiceBlocking.of(
                ApacheHttpClientChannels.create(server.getClientConfiguration()), DIALOGUE_RUNTIME);
    }

    public static SingleQueryParamServiceBlocking singleQueryParamService(VerificationServerRule server) {
        return SingleQueryParamServiceBlocking.of(
                ApacheHttpClientChannels.create(server.getClientConfiguration()), DIALOGUE_RUNTIME);
    }
}
