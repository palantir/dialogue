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
import com.google.common.net.HttpHeaders;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

public final class TestResponse implements Response {

    private final CloseRecordingInputStream inputStream;
    private final ResponseAttachments attachments = ResponseAttachments.create();

    private boolean closeCalled = false;
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
    public ResponseAttachments attachments() {
        return attachments;
    }

    @Override
    public void close() {
        checkNotClosed();
        try {
            closeCalled = true;
            inputStream.close();
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to close", e);
        }
    }

    public boolean isClosed() {
        return closeCalled;
    }

    private void checkNotClosed() {
        Preconditions.checkState(!isClosed(), "Please don't close twice");
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
