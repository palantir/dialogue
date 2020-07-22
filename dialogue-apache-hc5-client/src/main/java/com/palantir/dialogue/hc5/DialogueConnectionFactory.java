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
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.client5.http.impl.io.DefaultHttpResponseParserFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.hc.core5.http.io.HttpConnectionFactory;

/**
 * This {@link HttpConnectionFactory} is equivalent to the default factory, but provides
 * {@link DialogueManagedHttpClientConnection} connections in order to provide the functionality
 * proposed in <a href="https://issues.apache.org/jira/browse/HTTPCORE-639">HTTPCORE-639</a>.
 */
enum DialogueConnectionFactory implements HttpConnectionFactory<ManagedHttpClientConnection> {
    INSTANCE;

    private static final AtomicLong COUNTER = new AtomicLong();

    @Override
    public ManagedHttpClientConnection createConnection(Socket socket) throws IOException {
        ManagedHttpClientConnection conn = new DialogueManagedHttpClientConnection(
                "http-outgoing-" + COUNTER.getAndIncrement(),
                null,
                null,
                Http1Config.DEFAULT,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultHttpRequestWriterFactory.INSTANCE,
                DefaultHttpResponseParserFactory.INSTANCE);
        if (socket != null) {
            conn.bind(socket);
        }
        return conn;
    }
}
