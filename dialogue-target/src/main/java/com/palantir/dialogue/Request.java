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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/** Defines the parameters of a single call to an {@link Endpoint}. */
@ThreadSafe
public final class Request {

    private final ListMultimap<String, String> headerParams;
    private final ListMultimap<String, String> queryParams;
    private final ListMultimap<String, String> pathParams;
    private final Optional<RequestBody> body;
    private final RequestAttachments attachments;

    private Request(Builder builder) {
        body = builder.body;
        headerParams = builder.unmodifiableHeaderParams();
        queryParams = builder.unmodifiableQueryParams();
        pathParams = builder.unmodifiablePathParams();
        this.attachments = builder.attachments != null ? builder.attachments : RequestAttachments.create();
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
     *
     * @deprecated in favor of {@link #pathParameters()} which returns a multimap
     */
    @Deprecated
    public Map<String, String> pathParams() {
        return MultimapAsMap.of(pathParams);
    }

    /**
     * The HTTP path parameters for this request, encoded as a multimap of {@code param-name: param-value}. There is a
     * one-to-many correspondence between variable {@link PathTemplate.Segment path segments} of a {@link PathTemplate}
     * and the request's {@link #pathParams}.
     */
    public ListMultimap<String, String> pathParameters() {
        return pathParams;
    }

    /** The HTTP request body for this request or empty if this request does not contain a body. */
    public Optional<RequestBody> body() {
        return body;
    }

    /**
     * The mutable request attachments for this request. Attachments will be propagated when this request is mutated
     * through the builder
     */
    public RequestAttachments attachments() {
        return attachments;
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

        @SuppressWarnings("UnnecessaryLambda") // Avoid unnecessary allocation
        private static final com.google.common.base.Supplier<List<String>> MAP_VALUE_FACTORY = () -> new ArrayList<>(1);

        private static final int MUTABLE_HEADERS_MASK = 1;
        private static final int MUTABLE_QUERY_MASK = 1 << 1;
        private static final int MUTABLE_PATH_MASK = 1 << 2;

        private ListMultimap<String, String> headerParams = ImmutableListMultimap.of();

        private ListMultimap<String, String> queryParams = ImmutableListMultimap.of();

        private ListMultimap<String, String> pathParams = ImmutableListMultimap.of();

        private Optional<RequestBody> body = Optional.empty();

        @Nullable
        private RequestAttachments attachments;

        private int mutableCollectionsBitSet = 0;

        private Builder() {}

        public Request.Builder from(Request existing) {
            Preconditions.checkNotNull(existing, "Request.build().from() requires a non-null instance");

            headerParams = existing.headerParams;
            queryParams = existing.queryParams;
            pathParams = existing.pathParams;
            attachments = existing.attachments;

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
            Preconditions.checkArgumentNotNull(key, "Header name must not be null");
            mutableHeaderParams().put(key, value);
            return this;
        }

        public Request.Builder headerParams(Multimap<String, ? extends String> entries) {
            mutableHeaderParams().clear();
            return putAllHeaderParams(entries);
        }

        public Request.Builder putAllHeaderParams(String key, Iterable<String> values) {
            Preconditions.checkArgumentNotNull(key, "Header name must not be null");
            mutableHeaderParams().putAll(key, values);
            return this;
        }

        public Request.Builder putAllHeaderParams(Multimap<String, ? extends String> entries) {
            if (entries.containsKey(null)) {
                throw new SafeIllegalArgumentException("Header name must not be null");
            }
            mutableHeaderParams().putAll(entries);
            return this;
        }

        public Request.Builder putQueryParams(String key, String... values) {
            Preconditions.checkArgumentNotNull(key, "Query parameter name must not be null");
            for (String value : values) {
                Preconditions.checkArgumentNotNull(value, "Query parameter value must not be null");
            }
            mutableQueryParams().putAll(key, Arrays.asList(values));
            return this;
        }

        public Request.Builder putQueryParams(String key, String value) {
            Preconditions.checkArgumentNotNull(key, "Query parameter name must not be null");
            Preconditions.checkArgumentNotNull(value, "Query parameter value must not be null");
            mutableQueryParams().put(key, value);
            return this;
        }

        public Request.Builder putQueryParams(Map.Entry<String, ? extends String> entry) {
            Preconditions.checkArgumentNotNull(entry.getKey(), "Query parameter name must not be null");
            Preconditions.checkArgumentNotNull(entry.getValue(), "Query parameter value must not be null");
            mutableQueryParams().put(entry.getKey(), entry.getValue());
            return this;
        }

        public Request.Builder queryParams(Multimap<String, ? extends String> entries) {
            mutableQueryParams().clear();
            return putAllQueryParams(entries);
        }

        public Request.Builder putAllQueryParams(String key, Iterable<String> values) {
            Preconditions.checkArgumentNotNull(key, "Query parameter name must not be null");
            for (String value : values) {
                Preconditions.checkArgumentNotNull(value, "Query parameter value must not be null");
            }
            mutableQueryParams().putAll(key, values);
            return this;
        }

        public Request.Builder putAllQueryParams(Multimap<String, ? extends String> entries) {
            entries.forEach((key, value) -> {
                Preconditions.checkArgumentNotNull(key, "Query parameter name must not be null");
                Preconditions.checkArgumentNotNull(value, "Query parameter value must not be null");
            });
            mutableQueryParams().putAll(entries);
            return this;
        }

        public Request.Builder putPathParams(String key, String value) {
            Preconditions.checkArgumentNotNull(key, "Path parameter name must not be null");
            Preconditions.checkArgumentNotNull(value, "Path parameter value must not be null");
            mutablePathParams().put(key, value);
            return this;
        }

        public Request.Builder putPathParams(Map.Entry<String, ? extends String> entry) {
            Preconditions.checkArgumentNotNull(entry.getKey(), "Path parameter name must not be null");
            Preconditions.checkArgumentNotNull(entry.getValue(), "Path parameter value must not be null");
            mutablePathParams().put(entry.getKey(), entry.getValue());
            return this;
        }

        public Request.Builder pathParams(Map<String, ? extends String> entries) {
            mutablePathParams().clear();
            return putAllPathParams(entries);
        }

        public Request.Builder putAllPathParams(String key, Iterable<String> values) {
            Preconditions.checkArgumentNotNull(key, "Path parameter name must not be null");
            for (String value : values) {
                Preconditions.checkArgumentNotNull(value, "Path parameter value must not be null");
            }
            mutablePathParams().putAll(key, values);
            return this;
        }

        public Request.Builder putAllPathParams(Map<String, ? extends String> entries) {
            entries.forEach((key, value) -> {
                Preconditions.checkArgumentNotNull(key, "Path parameter name must not be null");
                Preconditions.checkArgumentNotNull(value, "Path parameter value must not be null");
            });
            entries.forEach(mutablePathParams()::put);
            return this;
        }

        public Request.Builder putAllPathParams(Multimap<String, ? extends String> entries) {
            entries.forEach((key, value) -> {
                Preconditions.checkArgumentNotNull(key, "Path parameter name must not be null");
                Preconditions.checkArgumentNotNull(value, "Path parameter value must not be null");
            });
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
            if (!isHeaderMutable()) {
                setHeaderMutable();
                ListMultimap<String, String> mutable =
                        Multimaps.newListMultimap(new TreeMap<>(String.CASE_INSENSITIVE_ORDER), MAP_VALUE_FACTORY);
                if (!headerParams.isEmpty()) {
                    // Outperforms mutable.putAll(headerParams)
                    headerParams.forEach(mutable::put);
                }
                headerParams = mutable;
            }
            return headerParams;
        }

        private ListMultimap<String, String> mutableQueryParams() {
            if (!isQueryMutable()) {
                setQueryMutable();
                queryParams = Multimaps.newListMultimap(new LinkedHashMap<>(), MAP_VALUE_FACTORY);
            }
            return queryParams;
        }

        private ListMultimap<String, String> mutablePathParams() {
            if (!isPathMutable()) {
                setPathMutable();
                pathParams = ArrayListMultimap.create(pathParams);
            }
            return pathParams;
        }

        private ListMultimap<String, String> unmodifiableHeaderParams() {
            return isHeaderMutable() ? Multimaps.unmodifiableListMultimap(headerParams) : headerParams;
        }

        private ListMultimap<String, String> unmodifiableQueryParams() {
            return isQueryMutable() ? Multimaps.unmodifiableListMultimap(queryParams) : queryParams;
        }

        private ListMultimap<String, String> unmodifiablePathParams() {
            return isPathMutable() ? Multimaps.unmodifiableListMultimap(pathParams) : pathParams;
        }

        public Request build() {
            return new Request(this);
        }

        private boolean isQueryMutable() {
            return getBitFlag(MUTABLE_QUERY_MASK);
        }

        private void setQueryMutable() {
            setBitFlag(MUTABLE_QUERY_MASK);
        }

        private boolean isHeaderMutable() {
            return getBitFlag(MUTABLE_HEADERS_MASK);
        }

        private void setHeaderMutable() {
            setBitFlag(MUTABLE_HEADERS_MASK);
        }

        private boolean isPathMutable() {
            return getBitFlag(MUTABLE_PATH_MASK);
        }

        private void setPathMutable() {
            setBitFlag(MUTABLE_PATH_MASK);
        }

        private boolean getBitFlag(int mask) {
            return (mutableCollectionsBitSet & mask) != 0;
        }

        private void setBitFlag(int flag) {
            mutableCollectionsBitSet |= flag;
        }
    }
}
