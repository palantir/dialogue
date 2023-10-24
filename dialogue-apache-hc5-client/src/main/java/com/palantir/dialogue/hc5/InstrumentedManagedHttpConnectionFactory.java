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

package com.palantir.dialogue.hc5;

import com.codahale.metrics.Timer;
import com.palantir.dialogue.hc5.DialogueClientMetrics.ConnectionSocketBind_Result;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.net.Socket;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;

/** Wrapper around another {@link HttpConnectionFactory} which adds instrumentation to the returned connection. */
final class InstrumentedManagedHttpConnectionFactory implements HttpConnectionFactory<ManagedHttpClientConnection> {

    private final HttpConnectionFactory<ManagedHttpClientConnection> delegate;
    private final Timer serverTimingOverhead;
    private final Timer socketBindSuccessesTimer;
    private final Timer socketBindFailureTimer;

    InstrumentedManagedHttpConnectionFactory(
            HttpConnectionFactory<ManagedHttpClientConnection> delegate,
            TaggedMetricRegistry metrics,
            String clientName) {
        this.delegate = delegate;
        this.serverTimingOverhead = DialogueClientMetrics.of(metrics).serverTimingOverhead(clientName);
        this.socketBindSuccessesTimer = DialogueClientMetrics.of(metrics)
                .connectionSocketBind()
                .clientName(clientName)
                .result(ConnectionSocketBind_Result.SUCCESS)
                .build();
        this.socketBindFailureTimer = DialogueClientMetrics.of(metrics)
                .connectionSocketBind()
                .clientName(clientName)
                .result(ConnectionSocketBind_Result.FAILURE)
                .build();
    }

    @Override
    public ManagedHttpClientConnection createConnection(Socket socket) throws IOException {
        return new InstrumentedManagedHttpClientConnection(
                delegate.createConnection(socket),
                serverTimingOverhead,
                socketBindSuccessesTimer,
                socketBindFailureTimer);
    }

    @Override
    public String toString() {
        return "InstrumentedManagedHttpConnectionFactory{" + delegate + '}';
    }
}
