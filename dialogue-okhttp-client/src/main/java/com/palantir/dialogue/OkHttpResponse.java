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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import okhttp3.ResponseBody;

public final class OkHttpResponse implements Response {

    private final okhttp3.Response delegate;
    private final ResponseAttachments attachments = ResponseAttachments.create();

    private OkHttpResponse(okhttp3.Response delegate) {
        this.delegate = delegate;
    }

    /** Wraps the given OkHttp {@link okhttp3.Response} into as a {@link Response}. */
    static OkHttpResponse wrap(okhttp3.Response delegate) {
        return new OkHttpResponse(delegate);
    }

    @Override
    public InputStream body() {
        ResponseBody responseBody = delegate.body();
        if (responseBody != null) {
            return responseBody.byteStream();
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int code() {
        return delegate.code();
    }

    @Override
    public ListMultimap<String, String> headers() {
        ListMultimap<String, String> headers = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                .arrayListValues()
                .build();
        delegate.headers().toMultimap().forEach(headers::putAll);
        return headers;
    }

    @Override
    public ResponseAttachments attachments() {
        return attachments;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
