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

package com.palantir.conjure.java.dialogue.serde;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.ws.rs.core.HttpHeaders;

final class TestResponse implements Response {

    private final CloseRecordingInputStream inputStream =
            new CloseRecordingInputStream(new ByteArrayInputStream(new byte[] {}));

    private int code = 0;
    private ListMultimap<String, String> headers = ImmutableListMultimap.of();

    @Override
    public CloseRecordingInputStream body() {
        return inputStream;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public ListMultimap<String, String> headers() {
        return headers;
    }

    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to close", e);
        }
    }

    TestResponse code(int code) {
        this.code = code;
        return this;
    }

    TestResponse contentType(String contentType) {
        this.headers = ImmutableListMultimap.of(HttpHeaders.CONTENT_TYPE, contentType);
        return this;
    }
}
