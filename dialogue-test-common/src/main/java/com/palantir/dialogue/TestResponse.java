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

package com.palantir.dialogue;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.ws.rs.core.HttpHeaders;

public final class TestResponse implements Response {

    private final CloseRecordingInputStream inputStream;

    private Optional<Throwable> closeCalled = Optional.empty();
    private int code = 0;
    private ListMultimap<String, String> headers = ImmutableListMultimap.of();

    public TestResponse() {
        this(new byte[] {});
    }

    public TestResponse(byte[] bytes) {
        this.inputStream = new CloseRecordingInputStream(new ByteArrayInputStream(bytes));
    }

    public static TestResponse withBody(@Nullable String body) {
        return new TestResponse(body == null ? new byte[] {} : body.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public CloseRecordingInputStream body() {
        return inputStream;
    }

    @Override
    public int code() {
        return code;
    }

    @CheckReturnValue
    public TestResponse code(int value) {
        this.code = value;
        return this;
    }

    @Override
    public ListMultimap<String, String> headers() {
        return headers;
    }

    @Override
    public void close() {
        checkNotClosed();
        try {
            closeCalled = Optional.of(new SafeRuntimeException("Close called here"));
            inputStream.close();
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to close", e);
        }
    }

    public boolean isClosed() {
        return closeCalled.isPresent();
    }

    private void checkNotClosed() {
        if (closeCalled.isPresent()) {
            throw new SafeRuntimeException("Please don't close twice", closeCalled.get());
        }
    }

    @CheckReturnValue
    public TestResponse contentType(String contentType) {
        return withHeader(HttpHeaders.CONTENT_TYPE, contentType);
    }

    @CheckReturnValue
    public TestResponse withHeader(String headerName, String headerValue) {
        this.headers = ImmutableListMultimap.<String, String>builder()
                .putAll(headers)
                .put(headerName, headerValue)
                .build();
        return this;
    }
}
