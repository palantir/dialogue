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

package com.palantir.dialogue.hc5;

import com.codahale.metrics.Timer;
import com.palantir.dialogue.hc5.DialogueClientMetrics.ConnectionConnect_Address;
import com.palantir.dialogue.hc5.DialogueClientMetrics.ConnectionConnect_Result;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import org.apache.hc.core5.http.protocol.HttpContext;

final class ConnectInstrumentation {

    private final Timer connectTimerSuccessDnsLookup;
    private final Timer connectTimerFailureDnsLookup;
    private final Timer connectTimerSuccessPreResolved;
    private final Timer connectTimerFailurePreResolved;

    ConnectInstrumentation(TaggedMetricRegistry registry, @Safe String clientName) {
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        this.connectTimerSuccessDnsLookup = metrics.connectionConnect()
                .clientName(clientName)
                .result(ConnectionConnect_Result.SUCCESS)
                .address(ConnectionConnect_Address.DNS_LOOKUP)
                .build();
        this.connectTimerFailureDnsLookup = metrics.connectionConnect()
                .clientName(clientName)
                .result(ConnectionConnect_Result.FAILURE)
                .address(ConnectionConnect_Address.DNS_LOOKUP)
                .build();
        this.connectTimerSuccessPreResolved = metrics.connectionConnect()
                .clientName(clientName)
                .result(ConnectionConnect_Result.SUCCESS)
                .address(ConnectionConnect_Address.PRE_RESOLVED)
                .build();
        this.connectTimerFailurePreResolved = metrics.connectionConnect()
                .clientName(clientName)
                .result(ConnectionConnect_Result.FAILURE)
                .address(ConnectionConnect_Address.PRE_RESOLVED)
                .build();
    }

    Timer timer(boolean success, HttpContext context) {
        if (DialogueRoutePlanner.hasPreResolvedAddress(context)) {
            return success ? connectTimerSuccessPreResolved : connectTimerFailurePreResolved;
        } else {
            return success ? connectTimerSuccessDnsLookup : connectTimerFailureDnsLookup;
        }
    }
}
