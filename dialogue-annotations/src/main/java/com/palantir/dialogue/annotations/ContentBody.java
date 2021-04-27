/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

public interface ContentBody extends Closeable {

    /** The content of this request body, possibly empty. */
    void writeTo(OutputStream output) throws IOException;

    /** A HTTP content type (e.g., "application/json") indicating the type of content. */
    String contentType();

    default OptionalLong contentLength() {
        return OptionalLong.empty();
    }

    /**
     * Closes this {@link ContentBody} and releases all resources. Calling {@link #close()} should never throw,
     * preferring to catch and log.
     */
    @Override
    void close();

    static ContentBody file(String contentType, Path filePath) {
        return new com.palantir.dialogue.annotations.ContentBody() {
            @Override
            public void writeTo(OutputStream output) throws IOException {
                Files.copy(filePath, output);
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public void close() {
                // Noop
            }
        };
    }
}
