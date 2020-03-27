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

import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.assertj.core.api.Assertions;

/** A test-only inputstream which can only be closed once. */
final class CloseRecordingInputStream extends InputStream {

    private final InputStream delegate;
    private Optional<Throwable> closeCalled = Optional.empty();

    CloseRecordingInputStream(InputStream delegate) {
        this.delegate = delegate;
    }

    boolean isClosed() {
        return closeCalled.isPresent();
    }

    void assertNotClosed() {
        if (closeCalled.isPresent()) {
            Assertions.fail("Expected CloseRecordingInputStream to be open but was closed", closeCalled.get());
        }
    }

    @Override
    public int read() throws IOException {
        assertNotClosed();
        return delegate.read();
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        assertNotClosed();
        return delegate.read(bytes);
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        assertNotClosed();
        return delegate.read(bytes, off, len);
    }

    @Override
    public int available() throws IOException {
        assertNotClosed();
        return delegate.available();
    }

    @Override
    public void reset() throws IOException {
        assertNotClosed();
        delegate.reset();
    }

    @Override
    public void mark(int readlimit) {
        assertNotClosed();
        delegate.mark(readlimit);
    }

    @Override
    public long skip(long num) throws IOException {
        assertNotClosed();
        return delegate.skip(num);
    }

    @Override
    public void close() throws IOException {
        closeCalled = Optional.of(new SafeRuntimeException("close was called here"));
        delegate.close();
    }
}
