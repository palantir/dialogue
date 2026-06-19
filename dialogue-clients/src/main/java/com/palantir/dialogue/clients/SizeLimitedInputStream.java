/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.clients;

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper class that limits the number of bytes read from an underlying {@link InputStream}.
 * Upon reading more bytes than the specified limit, a {@link SafeIllegalStateException} is thrown.
 * <p>
 * This does not account for skipped bytes, the goal being to protect against memory consumption from reading too many
 * bytes.
 */
final class SizeLimitedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long bytesRead = 0;

    SizeLimitedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    // We only need to override the below two methods, because all other read methods delegate to them
    // All read methods are tested in SizeLimitedInputStreamTest to confirm this
    // Note that this does not cover new read methods that could be added in the future which may not delegate to the
    // below methods, but we can't protect against this as far as I know, and it's highly unlikely that this will
    // happen

    @Override
    public int read() throws IOException {
        int read = super.read();
        if (read != -1 && bytesRead++ > maxBytes) {
            throw new SafeIllegalStateException("Exceeded maximum allowed bytes read", SafeArg.of("limit", maxBytes));
        }
        return read;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int count = super.read(buf, off, len);
        if (count > 0) {
            bytesRead += count;
            if (bytesRead > maxBytes) {
                throw new SafeIllegalStateException(
                        "Exceeded maximum allowed bytes read", SafeArg.of("limit", maxBytes));
            }
        }
        return count;
    }
}
