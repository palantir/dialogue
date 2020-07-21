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

import java.io.IOException;
import java.net.Socket;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;

/** Wrapper around another {@link HttpConnectionFactory} which adds tracing spans to the returned connection. */
final class TracedManagedHttpConnectionFactory implements HttpConnectionFactory<ManagedHttpClientConnection> {

    private final HttpConnectionFactory<ManagedHttpClientConnection> delegate;

    TracedManagedHttpConnectionFactory(HttpConnectionFactory<ManagedHttpClientConnection> delegate) {
        this.delegate = delegate;
    }

    @Override
    public ManagedHttpClientConnection createConnection(Socket socket) throws IOException {
        return new TracedManagedHttpClientConnection(delegate.createConnection(socket));
    }

    @Override
    public String toString() {
        return "TracedManagedHttpConnectionFactory{" + delegate + '}';
    }
}
