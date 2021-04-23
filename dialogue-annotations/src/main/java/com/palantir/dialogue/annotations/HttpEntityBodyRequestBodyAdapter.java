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

import com.palantir.dialogue.RequestBody;
import com.palantir.logsafe.UnsafeArg;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts {@link HttpEntity} to a {@link RequestBody}.
 */
abstract class HttpEntityBodyRequestBodyAdapter implements RequestBody {

    private static final Logger log = LoggerFactory.getLogger(HttpEntityBodyRequestBodyAdapter.class);

    private final HttpEntity entity;

    protected HttpEntityBodyRequestBodyAdapter(HttpEntity entity) {
        this.entity = entity;
    }

    @Override
    public final void writeTo(OutputStream output) throws IOException {
        entity.writeTo(output);
    }

    @Override
    public final String contentType() {
        return entity.getContentType();
    }

    @Override
    public final boolean repeatable() {
        return entity.isRepeatable();
    }

    @Override
    public final void close() {
        try {
            entity.close();
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to close MultipartRequestBody {}", UnsafeArg.of("body", entity), e);
        }
    }
}
