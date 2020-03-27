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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;

abstract class ForwardingInputStream extends InputStream {

    /** All methods delegate to this instance. */
    @VisibleForTesting
    abstract InputStream delegate();

    @Override
    public int read() throws IOException {
        return delegate().read();
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return delegate().read(bytes);
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        return delegate().read(bytes, off, len);
    }

    @Override
    public long skip(long num) throws IOException {
        return delegate().skip(num);
    }

    @Override
    public int available() throws IOException {
        return delegate().available();
    }

    @Override
    public void close() throws IOException {
        delegate().close();
    }

    @Override
    public void mark(int readlimit) {
        delegate().mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        delegate().reset();
    }

    @Override
    public boolean markSupported() {
        return delegate().markSupported();
    }
}
