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

import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

final class PathContentBody implements ContentBody {

    private final String contentType;
    private final Path filePath;

    PathContentBody(String contentType, Path filePath) {
        this.contentType = Preconditions.checkNotNull(contentType, "contentType");
        this.filePath = Preconditions.checkNotNull(filePath, "filePath");
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        Files.copy(filePath, output);
    }

    @Override
    public boolean repeatable() {
        return true;
    }

    @Override
    public OptionalLong contentLength() {
        return OptionalLong.of(filePath.toFile().length());
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public void close() {
        // Noop
    }
}
