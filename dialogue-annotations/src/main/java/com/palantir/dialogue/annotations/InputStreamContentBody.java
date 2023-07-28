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
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class InputStreamContentBody implements ContentBody {

    private static final SafeLogger log = SafeLoggerFactory.get(InputStreamContentBody.class);

    private final String contentType;
    private final InputStream inputStream;

    InputStreamContentBody(String contentType, InputStream inputStream) {
        this.contentType = Preconditions.checkNotNull(contentType, "contentType");
        this.inputStream = Preconditions.checkNotNull(inputStream, "inputStream");
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        inputStream.transferTo(output);
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public boolean repeatable() {
        return false;
    }

    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (IOException e) {
            log.warn("Failed to close InputStreamContentBody {}", UnsafeArg.of("body", inputStream), e);
        }
    }
}
