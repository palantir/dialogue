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

package com.palantir.dialogue.hc5;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;

/** Simplest possible empty {@link HttpEntity} implementation. */
enum EmptyHttpEntity implements HttpEntity {
    INSTANCE;

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Nullable
    @Override
    public InputStream getContent() {
        return null;
    }

    @Override
    public void writeTo(OutputStream _outStream) {}

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Nullable
    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return null;
    }

    @Override
    public void close() {}

    @Override
    public long getContentLength() {
        return 0;
    }

    @Nullable
    @Override
    public String getContentType() {
        return null;
    }

    @Nullable
    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return "EmptyHttpEntity{}";
    }
}
