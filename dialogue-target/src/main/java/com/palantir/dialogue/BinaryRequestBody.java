/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.Preconditions;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Streamed binary request data with Content-Type <code>application/octet-stream</code>. */
public interface BinaryRequestBody extends Closeable {

    /** Invoked to write data to the request stream. */
    void write(OutputStream requestBody) throws IOException;

    /** Returns <pre>true</pre> if {@link #write(OutputStream)} may be invoked multiple times. */
    default boolean repeatable() {
        return false;
    }

    /** This method may be overridden to return resources. */
    @Override
    default void close() throws IOException {
        // nop
    }

    /** Create a {@link BinaryRequestBody} from an {@link InputStream} instance. */
    static BinaryRequestBody of(InputStream inputStream) {
        return new BinaryRequestBody() {
            private boolean invoked;

            @Override
            public void write(OutputStream requestBody) throws IOException {
                Preconditions.checkState(!invoked, "Write has already been called");
                invoked = true;
                inputStream.transferTo(requestBody);
            }

            @Override
            public boolean repeatable() {
                // The stream is exhausted as it is read, thus this is not repeatable.
                return false;
            }

            @Override
            public void close() throws IOException {
                inputStream.close();
            }
        };
    }
}
