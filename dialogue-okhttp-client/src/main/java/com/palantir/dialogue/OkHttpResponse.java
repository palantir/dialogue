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

import com.google.common.collect.Iterables;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OkHttpResponse implements Response {

    private final okhttp3.Response delegate;

    private OkHttpResponse(okhttp3.Response delegate) {
        this.delegate = delegate;
    }

    /** Wraps the given OkHttp {@link okhttp3.Response} into as a {@link Response}. */
    static OkHttpResponse wrap(okhttp3.Response delegate) {
        return new OkHttpResponse(delegate);
    }

    @Override
    public InputStream body() {
        // TODO(rfink): Empty bodies may not have a byte stream. Need to produce a zero-length stream.
        return delegate.body().byteStream();
    }

    @Override
    public int code() {
        return delegate.code();
    }

    @Override
    public Map<String, List<String>> headers() {
        return delegate.headers().toMultimap();
    }

    @Override
    public Optional<String> contentType() {
        // TODO(rfink): Maybe cache this
        // TODO(rfink): Header case sensitivity?
        Collection<String> headers = delegate.headers(Headers.CONTENT_TYPE);
        Preconditions.checkArgument(headers.size() <= 1, "Expected zero or one Content-Type headers",
                SafeArg.of("contentType", headers));
        return Optional.ofNullable(headers.size() == 1 ? Iterables.getOnlyElement(headers) : null);
    }
}
