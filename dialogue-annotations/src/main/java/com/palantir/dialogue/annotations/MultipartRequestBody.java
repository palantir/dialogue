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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.entity.mime.AbstractContentBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartPartBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

public final class MultipartRequestBody extends HttpEntityBodyRequestBodyAdapter {

    private MultipartRequestBody(HttpEntity entity) {
        super(entity);
    }

    public static final class Builder {

        private final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        private Builder() {}

        @VisibleForTesting
        Builder boundary(String boundary) {
            builder.setBoundary(boundary);
            return this;
        }

        public Builder addContentBodyPart(ContentBodyPartBuilder contentBodyPartBuilder) {
            builder.addPart(contentBodyPartBuilder.builder.build());
            return this;
        }

        public Builder addFormBodyPart(FormBodyPartBuilder formBodyPartBuilder) {
            builder.addPart(formBodyPartBuilder.builder.build());
            return this;
        }

        public MultipartRequestBody build() {
            return new MultipartRequestBody(builder.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ContentBodyPartBuilder contentBodyPartBuilder(ContentBody contentBody) {
        return new ContentBodyPartBuilder(contentBody);
    }

    public static FormBodyPartBuilder formBodyPartBuilder(String name, ContentBody contentBody) {
        return new FormBodyPartBuilder(name, contentBody);
    }

    public static final class FormBodyPartBuilder {
        private final ContentBodyAdapter bodyAdapter;
        private final org.apache.hc.client5.http.entity.mime.FormBodyPartBuilder builder;

        private FormBodyPartBuilder(String name, ContentBody unsafeContentBody) {
            bodyAdapter = new ContentBodyAdapter(unsafeContentBody);
            builder = org.apache.hc.client5.http.entity.mime.FormBodyPartBuilder.create(name, bodyAdapter);
        }

        public FormBodyPartBuilder fileName(String fileName) {
            bodyAdapter.setFileName(fileName);
            return this;
        }
    }

    public static final class ContentBodyPartBuilder {
        private final MultipartPartBuilder builder;

        private ContentBodyPartBuilder(ContentBody unsafeContentBody) {
            this.builder = MultipartPartBuilder.create(new ContentBodyAdapter(unsafeContentBody));
        }

        public ContentBodyPartBuilder addHeaderValue(String key, String value) {
            builder.addHeader(key, value);
            return this;
        }
    }

    private static final class ContentBodyAdapter extends AbstractContentBody {

        private final ContentBody unsafeRequestBody;

        @Nullable
        private String fileName;

        private ContentBodyAdapter(ContentBody requestBody) {
            super(ContentType.parse(requestBody.contentType()));
            this.unsafeRequestBody = requestBody;
        }

        void setFileName(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public long getContentLength() {
            return unsafeRequestBody.contentLength().orElse(-1);
        }

        @Override
        @Nullable
        public String getFilename() {
            return fileName;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try (ContentBody requestBody = unsafeRequestBody) {
                requestBody.writeTo(out);
            }
        }
    }
}
