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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;
import com.palantir.logsafe.Preconditions;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Defines the parameters of a single {@link Call} to an {@link Endpoint}. */
public final class Request {

    private final ImmutableSortedMap<String, String> headerParams;
    private final ImmutableMultimap<String, String> queryParams;
    private final ImmutableMap<String, String> pathParams;
    private final Optional<RequestBody> body;

    private Request(Builder builder) {
        body = builder.body;
        headerParams = builder.headerParams.build();
        queryParams = builder.queryParams.build();
        pathParams = builder.pathParams.build();
    }

    /**
     * The HTTP headers for this request, encoded as a map of {@code header-name: header-value}.
     * Headers names are compared in a case-insensitive fashion as per
     * https://tools.ietf.org/html/rfc7540#section-8.1.2.
     */
    public Map<String, String> headerParams() {
        return headerParams;
    }

    /**
     * The URL query parameters headers for this request. These should *not* be URL-encoded and {@link Channel}s are
     * responsible for performing the appropriate encoding if necessary.
     */
    public Multimap<String, String> queryParams() {
        return queryParams;
    }

    /**
     * The HTTP path parameters for this request, encoded as a map of {@code param-name: param-value}. There is a
     * one-to-one correspondence between variable {@link PathTemplate.Segment path segments} of a {@link PathTemplate}
     * and the request's {@link #pathParams}.
     */
    public Map<String, String> pathParams() {
        return pathParams;
    }

    /** The HTTP request body for this request or empty if this request does not contain a body. */
    public Optional<RequestBody> body() {
        return body;
    }

    @Override
    public String toString() {
        return "Request{"
                // Values are excluded to avoid the risk of logging credentials
                + "headerParamsKeys="
                + headerParams.keySet()
                + ", queryParams="
                + queryParams
                + ", pathParams="
                + pathParams
                + ", body="
                + body
                + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Request request = (Request) other;
        return headerParams.equals(request.headerParams)
                && queryParams.equals(request.queryParams)
                && pathParams.equals(request.pathParams)
                && body.equals(request.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headerParams, queryParams, pathParams, body);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ImmutableSortedMap.Builder<String, String> headerParams =
                ImmutableSortedMap.orderedBy(String.CASE_INSENSITIVE_ORDER);
        private ImmutableMultimap.Builder<String, String> queryParams = ImmutableMultimap.builder();
        private ImmutableMap.Builder<String, String> pathParams = ImmutableMap.builder();
        private Optional<RequestBody> body = Optional.empty();

        private Builder() {}

        public Request.Builder from(Request instance) {
            Preconditions.checkNotNull(instance, "instance");
            putAllHeaderParams(instance.headerParams());
            putAllQueryParams(instance.queryParams());
            putAllPathParams(instance.pathParams());
            Optional<RequestBody> bodyOptional = instance.body();
            if (bodyOptional.isPresent()) {
                body(bodyOptional);
            }
            return this;
        }

        public Request.Builder putHeaderParams(String key, String value) {
            headerParams.put(key, value);
            return this;
        }

        public Request.Builder putHeaderParams(Map.Entry<String, ? extends String> entry) {
            headerParams.put(entry);
            return this;
        }

        public Request.Builder headerParams(Map<String, ? extends String> entries) {
            headerParams = ImmutableSortedMap.orderedBy(String.CASE_INSENSITIVE_ORDER);
            return putAllHeaderParams(entries);
        }

        public Request.Builder putAllHeaderParams(Map<String, ? extends String> entries) {
            this.headerParams.putAll(entries);
            return this;
        }

        public Request.Builder putQueryParams(String key, String... values) {
            queryParams.putAll(key, values);
            return this;
        }

        public Request.Builder putQueryParams(String key, String value) {
            queryParams.put(key, value);
            return this;
        }

        public Request.Builder putQueryParams(Map.Entry<String, ? extends String> entry) {
            queryParams.put(entry);
            return this;
        }

        public Request.Builder queryParams(Multimap<String, ? extends String> entries) {
            queryParams = ImmutableMultimap.builder();
            return putAllQueryParams(entries);
        }

        public Request.Builder putAllQueryParams(String key, Iterable<String> values) {
            queryParams.putAll(key, values);
            return this;
        }

        public Request.Builder putAllQueryParams(Multimap<String, ? extends String> entries) {
            queryParams.putAll(entries);
            return this;
        }

        public Request.Builder putPathParams(String key, String value) {
            pathParams.put(key, value);
            return this;
        }

        public Request.Builder putPathParams(Map.Entry<String, ? extends String> entry) {
            pathParams.put(entry);
            return this;
        }

        public Request.Builder pathParams(Map<String, ? extends String> entries) {
            pathParams = ImmutableMap.builder();
            return putAllPathParams(entries);
        }

        public Request.Builder putAllPathParams(Map<String, ? extends String> entries) {
            pathParams.putAll(entries);
            return this;
        }

        public Request.Builder body(RequestBody value) {
            body = Optional.of(Preconditions.checkNotNull(value, "body"));
            return this;
        }

        @SuppressWarnings("unchecked")
        public Request.Builder body(Optional<? extends RequestBody> value) {
            body = (Optional<RequestBody>) value;
            return this;
        }

        public Request build() {
            return new Request(this);
        }
    }
}
