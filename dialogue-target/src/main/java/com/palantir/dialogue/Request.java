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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/** Defines the parameters of a single call to an {@link Endpoint}. */
@ThreadSafe
public final class Request {

    private final ListMultimap<String, String> headerParams;
    private final ListMultimap<String, String> queryParams;
    private final Map<String, String> pathParams;
    private final Optional<RequestBody> body;
    private Optional<CallingThreadExecutor> callingThreadExecutor = Optional.empty();

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

    public synchronized void executeInCallingThread() {
        this.callingThreadExecutor = Optional.of(new DefaultCallingThreadExecutor());
    }

    public synchronized Optional<CallingThreadExecutor> getCallingThreadExecutor() {
        return callingThreadExecutor;
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

        private Map<String, String> pathParams = ImmutableMap.of();

        private Optional<RequestBody> body = Optional.empty();

        private int mutableCollectionsBitSet = 0;

        private Builder() {}

        public Request.Builder from(Request existing) {
            Preconditions.checkNotNull(existing, "Request.build().from() requires a non-null instance");

            headerParams = existing.headerParams;
            queryParams = existing.queryParams;
            pathParams = existing.pathParams;

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
            mutableQueryParams().putAll(key, Arrays.asList(values));
            return this;
        }

        public Request.Builder putQueryParams(String key, String value) {
            mutableQueryParams().put(key, value);
            return this;
        }

        public Request.Builder putQueryParams(Map.Entry<String, ? extends String> entry) {
            mutableQueryParams().put(entry.getKey(), entry.getValue());
            return this;
        }

        public Request.Builder queryParams(Multimap<String, ? extends String> entries) {
            mutableQueryParams().clear();
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
            mutablePathParams().put(entry.getKey(), entry.getValue());
            return this;
        }

        public Request.Builder pathParams(Map<String, ? extends String> entries) {
            mutablePathParams().clear();
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
                queryParams = ArrayListMultimap.create(queryParams);
            }
            return queryParams;
        }

        private Map<String, String> mutablePathParams() {
            if (!isPathMutable()) {
                setPathMutable();
                pathParams = new HashMap<>(pathParams);
            }
            return pathParams;
        }

        private ListMultimap<String, String> unmodifiableHeaderParams() {
            return isHeaderMutable() ? Multimaps.unmodifiableListMultimap(headerParams) : headerParams;
        }

        private ListMultimap<String, String> unmodifiableQueryParams() {
            return isQueryMutable() ? Multimaps.unmodifiableListMultimap(queryParams) : queryParams;
        }

        private Map<String, String> unmodifiablePathParams() {
            return isPathMutable() ? Collections.unmodifiableMap(pathParams) : pathParams;
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
