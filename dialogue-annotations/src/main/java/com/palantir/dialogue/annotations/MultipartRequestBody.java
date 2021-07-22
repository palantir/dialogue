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
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.palantir.dialogue.RequestBody;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.entity.mime.AbstractContentBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartPart;
import org.apache.hc.client5.http.entity.mime.MultipartPartBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

public final class MultipartRequestBody implements RequestBody {

    private static final SafeLogger log = SafeLoggerFactory.get(MultipartRequestBody.class);

    private final HttpEntity httpEntity;
    private final List<Part> parts;

    private MultipartRequestBody(HttpEntity httpEntity, List<Part> parts) {
        this.httpEntity = Preconditions.checkNotNull(httpEntity, "httpEntity");
        this.parts = ImmutableList.copyOf(Preconditions.checkNotNull(parts, "parts"));
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        httpEntity.writeTo(output);
    }

    @Override
    public String contentType() {
        return httpEntity.getContentType();
    }

    @Override
    public boolean repeatable() {
        return parts.stream().allMatch(part -> part.contentBody.repeatable());
    }

    @Override
    public OptionalLong contentLength() {
        long contentLength = httpEntity.getContentLength();
        return contentLength != -1 ? OptionalLong.of(contentLength) : OptionalLong.empty();
    }

    @Override
    public void close() {
        try (Closer closer = Closer.create()) {
            parts.forEach(part -> closer.register(part.contentBody));
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to close MultipartRequestBody {}", e);
        }
    }

    public static final class Builder {

        private final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        private List<Part> parts = new ArrayList<>();

        private Builder() {}

        @VisibleForTesting
        Builder boundary(String boundary) {
            builder.setBoundary(boundary);
            return this;
        }

        public Builder addPart(Part part) {
            Preconditions.checkNotNull(part, "part");
            builder.addPart(part.part);
            parts.add(part);
            return this;
        }

        public MultipartRequestBody build() {
            return new MultipartRequestBody(builder.build(), parts);
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

        private FormBodyPartBuilder(String name, ContentBody contentBody) {
            bodyAdapter = new ContentBodyAdapter(contentBody);
            builder = org.apache.hc.client5.http.entity.mime.FormBodyPartBuilder.create(name, bodyAdapter);
        }

        public FormBodyPartBuilder fileName(String fileName) {
            bodyAdapter.setFileName(fileName);
            return this;
        }

        public Part build() {
            return new Part(builder.build(), bodyAdapter.contentBody);
        }
    }

    public static final class ContentBodyPartBuilder {
        private final ContentBodyAdapter bodyAdapter;
        private final MultipartPartBuilder builder;

        private ContentBodyPartBuilder(ContentBody contentBody) {
            Preconditions.checkNotNull(contentBody, "contentBody");
            bodyAdapter = new ContentBodyAdapter(contentBody);
            builder = MultipartPartBuilder.create(bodyAdapter);
        }

        public ContentBodyPartBuilder addHeaderValue(String key, String value) {
            builder.addHeader(key, value);
            return this;
        }

        public Part build() {
            return new Part(builder.build(), bodyAdapter.contentBody);
        }
    }

    public static final class Part {
        private final MultipartPart part;
        private final ContentBody contentBody;

        private Part(MultipartPart part, ContentBody contentBody) {
            this.part = Preconditions.checkNotNull(part, "part");
            this.contentBody = contentBody;
        }
    }

    private static final class ContentBodyAdapter extends AbstractContentBody {

        private final ContentBody contentBody;

        @Nullable
        private String fileName;

        private ContentBodyAdapter(ContentBody contentBody) {
            super(Preconditions.checkNotNull(
                    ContentType.parse(contentBody.contentType()),
                    "Invalid content type",
                    SafeArg.of("contentType", contentBody.contentType())));
            this.contentBody = contentBody;
        }

        void setFileName(@Nullable String fileName) {
            this.fileName = fileName;
        }

        @Override
        public long getContentLength() {
            return contentBody.contentLength().orElse(-1);
        }

        @Override
        @Nullable
        public String getFilename() {
            return fileName;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            contentBody.writeTo(out);
        }
    }
}
