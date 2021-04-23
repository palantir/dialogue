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
import com.palantir.dialogue.RequestBody;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.hc.client5.http.entity.mime.AbstractContentBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartPartBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

/** An <a href="http://www.ietf.org/rfc/rfc2387.txt">RFC 2387</a>-compliant request body. */
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

        public Builder addRequestBodyPart(RequestBodyPartBuilder requestBodyPartBuilder) {
            builder.addPart(requestBodyPartBuilder.builder.build());
            return this;
        }

        public MultipartRequestBody build() {
            return new MultipartRequestBody(builder.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RequestBodyPartBuilder requestBodyPartBuilder(RequestBody unsafeRequestBody) {
        return new RequestBodyPartBuilder(unsafeRequestBody);
    }

    public static final class RequestBodyPartBuilder {
        private final MultipartPartBuilder builder;

        private RequestBodyPartBuilder(RequestBody unsafeRequestBody) {
            this.builder = MultipartPartBuilder.create(
                    new AbstractContentBody(ContentType.parse(unsafeRequestBody.contentType())) {
                        @Override
                        public long getContentLength() {
                            return -1;
                        }

                        @Override
                        public String getFilename() {
                            // Allowed
                            return null;
                        }

                        @Override
                        public void writeTo(OutputStream out) throws IOException {
                            try (RequestBody requestBody = unsafeRequestBody) {
                                requestBody.writeTo(out);
                            }
                        }
                    });
        }

        public RequestBodyPartBuilder addHeaderValue(String key, String value) {
            builder.addHeader(key, value);
            return this;
        }
    }
}
