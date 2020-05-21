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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.palantir.logsafe.Preconditions;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/** Defines the parameters of a single call to an {@link Endpoint}. */
@ThreadSafe
public final class Request {

    private final ListMultimap<String, String> headerParams;
    private final ImmutableListMultimap<String, String> queryParams;
    private final ImmutableMap<String, String> pathParams;
    private final Optional<RequestBody> body;

    private Request(Builder builder) {
        body = builder.body;
        headerParams = builder.unmodifiableHeaderParams();
        queryParams = builder.unmodifiableQueryParams();
        pathParams = builder.unmodifiablePathParams();
    }

    /**
     * The HTTP headers for this request, encoded as a map of {@code header-name: header-value}.
     * Headers names are compared in a case-insensitive fashion as per
     * https://tools.ietf.org/html/rfc7540#section-8.1.2.
     */
    public ListMultimap<String, String> headerParams() {
        return headerParams;
    }

    /**
     * The URL query parameters headers for this request. These should *not* be URL-encoded and {@link Channel}s are
     * responsible for performing the appropriate encoding if necessary.
     */
    public ListMultimap<String, String> queryParams() {
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

    @NotThreadSafe
    public static final class Builder {
        private static final ImmutableListMultimap<String, String> EMPTY =
                new ImmutableListMultimap.Builder<String, String>().build();

        @Nullable
        private ListMultimap<String, String> headerParams;

        @Nullable
        private ImmutableListMultimap.Builder<String, String> queryParams;

        @Nullable
        private ImmutableMap.Builder<String, String> pathParams;

        private Optional<RequestBody> body = Optional.empty();

        // To optimize the case where a builder doesn't end up modifying headers/queryparams/pathparams, we may store
        // references to another Request's internal objects. If we need to do a mutation, then we'll copy the elements
        // and null these out.
        @Nullable
        private ListMultimap<String, String> existingUnmodifiableHeaderParams;

        @Nullable
        private ImmutableListMultimap<String, String> existingQueryParams;

        @Nullable
        private ImmutableMap<String, String> existingPathParams;

        private Builder() {}

        public Request.Builder from(Request existing) {
            Preconditions.checkNotNull(existing, "Request.build().from() requires a non-null instance");

            existingUnmodifiableHeaderParams = Multimaps.unmodifiableListMultimap(existing.headerParams);
            existingQueryParams = existing.queryParams;
            existingPathParams = existing.pathParams;

            Optional<RequestBody> bodyOptional = existing.body();
            if (bodyOptional.isPresent()) {
                body(bodyOptional);
            }
            return this;
        }

        public Request.Builder putHeaderParams(String key, String... values) {
            return putAllHeaderParams(key, Arrays.asList(values));
        }

        public Request.Builder putHeaderParams(String key, String value) {
            mutableHeaderParams().put(key, value);
            return this;
        }

        public Request.Builder headerParams(Multimap<String, ? extends String> entries) {
            mutableHeaderParams().clear();
            return putAllHeaderParams(entries);
        }

        public Request.Builder putAllHeaderParams(String key, Iterable<String> values) {
            mutableHeaderParams().putAll(key, values);
            return this;
        }

        public Request.Builder putAllHeaderParams(Multimap<String, ? extends String> entries) {
            mutableHeaderParams().putAll(entries);
            return this;
        }

        public Request.Builder putQueryParams(String key, String... values) {
            mutableQueryParams().putAll(key, values);
            return this;
        }

        public Request.Builder putQueryParams(String key, String value) {
            mutableQueryParams().put(key, value);
            return this;
        }

        public Request.Builder putQueryParams(Map.Entry<String, ? extends String> entry) {
            mutableQueryParams().put(entry);
            return this;
        }

        public Request.Builder queryParams(Multimap<String, ? extends String> entries) {
            queryParams = ImmutableListMultimap.builder();
            return putAllQueryParams(entries);
        }

        public Request.Builder putAllQueryParams(String key, Iterable<String> values) {
            mutableQueryParams().putAll(key, values);
            return this;
        }

        public Request.Builder putAllQueryParams(Multimap<String, ? extends String> entries) {
            mutableQueryParams().putAll(entries);
            return this;
        }

        public Request.Builder putPathParams(String key, String value) {
            mutablePathParams().put(key, value);
            return this;
        }

        public Request.Builder putPathParams(Map.Entry<String, ? extends String> entry) {
            mutablePathParams().put(entry);
            return this;
        }

        public Request.Builder pathParams(Map<String, ? extends String> entries) {
            pathParams = ImmutableMap.builder();
            return putAllPathParams(entries);
        }

        public Request.Builder putAllPathParams(Map<String, ? extends String> entries) {
            mutablePathParams().putAll(entries);
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

        private ListMultimap<String, String> mutableHeaderParams() {
            if (headerParams == null) {
                headerParams = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                        .arrayListValues()
                        .build();

                if (existingUnmodifiableHeaderParams != null) {
                    headerParams.putAll(existingUnmodifiableHeaderParams);
                    existingUnmodifiableHeaderParams = null;
                }
            }
            return headerParams;
        }

        private ImmutableListMultimap.Builder<String, String> mutableQueryParams() {
            if (queryParams == null) {
                queryParams = ImmutableListMultimap.builder();

                if (existingQueryParams != null) {
                    queryParams.putAll(existingQueryParams);
                    existingQueryParams = null;
                }
            }
            return queryParams;
        }

        private ImmutableMap.Builder<String, String> mutablePathParams() {
            if (pathParams == null) {
                pathParams = ImmutableMap.builder();

                if (existingPathParams != null) {
                    pathParams.putAll(existingPathParams);
                    existingPathParams = null;
                }
            }
            return pathParams;
        }

        private ListMultimap<String, String> unmodifiableHeaderParams() {
            if (existingUnmodifiableHeaderParams != null) {
                return existingUnmodifiableHeaderParams;
            }

            if (headerParams != null) {
                return Multimaps.unmodifiableListMultimap(headerParams);
            }

            return EMPTY;
        }

        private ImmutableListMultimap<String, String> unmodifiableQueryParams() {
            if (existingQueryParams != null) {
                return existingQueryParams;
            }

            if (queryParams != null) {
                return queryParams.build();
            }

            return EMPTY;
        }

        private ImmutableMap<String, String> unmodifiablePathParams() {
            if (existingPathParams != null) {
                return existingPathParams;
            }

            if (pathParams != null) {
                return pathParams.build();
            }

            return ImmutableMap.of();
        }

        public Request build() {
            return new Request(this);
        }
    }
}
