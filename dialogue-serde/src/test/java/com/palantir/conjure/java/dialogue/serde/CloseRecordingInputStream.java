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

import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** A test-only inputstream which can only be closed once. */
final class CloseRecordingInputStream extends InputStream {

    private final InputStream delegate;
    private Optional<Throwable> closeCalled = Optional.empty();

    CloseRecordingInputStream(InputStream delegate) {
        this.delegate = delegate;
    }

    boolean isClosed() {
        return !closeCalled.isPresent();
    }

    @Override
    public int read() throws IOException {
        checkPrecondition();
        return delegate.read();
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        checkPrecondition();
        return delegate.read(bytes);
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        checkPrecondition();
        return delegate.read(bytes, off, len);
    }

    @Override
    public int available() throws IOException {
        checkPrecondition();
        return delegate.available();
    }

    @Override
    public void reset() throws IOException {
        checkPrecondition();
        delegate.reset();
    }

    @Override
    public void mark(int readlimit) {
        checkPrecondition();
        delegate.mark(readlimit);
    }

    @Override
    public long skip(long num) throws IOException {
        checkPrecondition();
        return delegate.skip(num);
    }

    @Override
    public void close() throws IOException {
        closeCalled = Optional.of(new RuntimeException("close was called here"));
        delegate.close();
    }

    private void checkPrecondition() {
        if (closeCalled.isPresent()) {
            throw new SafeIllegalStateException("Can't do stuff after InputStream closed", closeCalled.get());
        }
    }
}
