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

package com.palantir.dialogue.core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.net.InetAddresses;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/** Convenience utility around {@link UrlBuilder}. */
public final class BaseUrl {

    private final DefaultUrlBuilder builder;

    public static BaseUrl of(URL baseUrl) {
        return new BaseUrl(DefaultUrlBuilder.from(baseUrl));
    }

    private BaseUrl(DefaultUrlBuilder builder) {
        this.builder = builder;
    }

    public URL render(Endpoint endpoint, Request request) {
        DefaultUrlBuilder url = builder.newBuilder();
        endpoint.renderPath(request.pathParameters(), url);
        request.queryParams().forEach(url::queryParam);
        return url.build();
    }

    @Override
    public String toString() {
        return "BaseUrl{builder=" + builder + '}';
    }

    /** A simplistic URL builder, not tuned for performance. */
    @VisibleForTesting
    static final class DefaultUrlBuilder implements UrlBuilder {

        private static final Joiner PATH_JOINER = Joiner.on('/');
        private static final Joiner.MapJoiner QUERY_JOINER = Joiner.on('&').withKeyValueSeparator('=');

        @SuppressWarnings("UnnecessaryLambda") // Avoid unnecessary allocation
        private static final com.google.common.base.Supplier<List<String>> MAP_VALUE_FACTORY = () -> new ArrayList<>(1);

        private final String protocol;
        private final String host;
        private final int port;
        private final List<String> pathSegments;
        private final Multimap<String, String> queryNamesAndValues;

        static DefaultUrlBuilder from(URL baseUrl) {
            // Sanitize path syntax and strip all irrelevant URL components
            if (!baseUrl.getProtocol().equals("http") && !baseUrl.getProtocol().equals("https")) {
                throw new SafeIllegalArgumentException(
                        "unsupported protocol", SafeArg.of("protocol", baseUrl.getProtocol()));
            }
            if (Strings.emptyToNull(baseUrl.getQuery()) != null) {
                throw new SafeIllegalArgumentException(
                        "baseUrl query must be empty", UnsafeArg.of("query", baseUrl.getQuery()));
            }
            if (Strings.emptyToNull(baseUrl.getRef()) != null) {
                throw new SafeIllegalArgumentException(
                        "baseUrl ref must be empty", UnsafeArg.of("ref", baseUrl.getRef()));
            }
            if (Strings.emptyToNull(baseUrl.getUserInfo()) != null) {
                // the value of baseUrl.getUserInfo() may contain credential information and mustn't be logged
                throw new SafeIllegalArgumentException("baseUrl user info must be empty");
            }
            return new DefaultUrlBuilder(baseUrl);
        }

        private DefaultUrlBuilder(URL url) {
            this.protocol = url.getProtocol();
            this.host = url.getHost();
            this.port = url.getPort();
            this.pathSegments = new ArrayList<>();
            this.queryNamesAndValues = createQueryParameterMap();
            Preconditions.checkArgument(
                    port >= -1 && port <= 65535, "port must be in range [0, 65535] or default [-1]");
            String strippedBasePath = stripSlashes(url.getPath());
            if (!strippedBasePath.isEmpty()) {
                encodedPathSegments(strippedBasePath);
            }
        }

        DefaultUrlBuilder newBuilder() {
            return new DefaultUrlBuilder(this);
        }

        private DefaultUrlBuilder(DefaultUrlBuilder builder) {
            this.protocol = builder.protocol;
            this.host = builder.host;
            this.port = builder.port;
            this.pathSegments = new ArrayList<>(builder.pathSegments);
            this.queryNamesAndValues = createQueryParameterMap();
            queryNamesAndValues.putAll(builder.queryNamesAndValues);
        }

        private static ListMultimap<String, String> createQueryParameterMap() {
            return Multimaps.newListMultimap(new LinkedHashMap<>(), MAP_VALUE_FACTORY);
        }

        /**
         * Adds the given URL-encoded path segment (one or more) to the list of segments, or fails if the given segments
         * contain forbidden characters. Note that leading or trailing slashes are preserved, for instance {@code url
         * .pathSegment("foo").encodedPathSegments{"/bar/"}.pathSegment("baz")} yields a URL with path
         * {@code foo//bar//baz} (note the empty segments).
         */
        DefaultUrlBuilder encodedPathSegments(String segments) {
            if (!UrlEncoder.isPath(segments)) {
                throw new SafeIllegalArgumentException(
                        "invalid characters in encoded path segments", UnsafeArg.of("segments", segments));
            }
            this.pathSegments.add(segments);
            return this;
        }

        /** URL-encodes the given path segment and adds it to the list of segments. */
        @Override
        public DefaultUrlBuilder pathSegment(String thePath) {
            this.pathSegments.add(BaseUrl.UrlEncoder.encodePathSegment(thePath));
            return this;
        }

        @Override
        public DefaultUrlBuilder pathSegments(Collection<String> paths) {
            this.pathSegments.addAll(Collections2.transform(paths, BaseUrl.UrlEncoder::encodePathSegment));
            return this;
        }

        /**
         * URL-encodes the given query parameter name and value and adds them to the list of query parameters. Note that
         * no guarantee is made regarding the ordering of query parameters in the resulting URL.
         */
        @Override
        public DefaultUrlBuilder queryParam(String name, String value) {
            this.queryNamesAndValues.put(
                    BaseUrl.UrlEncoder.encodeQueryNameOrValue(name), BaseUrl.UrlEncoder.encodeQueryNameOrValue(value));
            return this;
        }

        URL build() {
            try {
                Preconditions.checkNotNull(protocol, "protocol must be set");
                Preconditions.checkNotNull(host, "host must be set");
                // Allow default ports
                Preconditions.checkArgument(port >= -1, "port must be set");

                StringBuilder file = new StringBuilder();
                encodePath(pathSegments, file);
                encodeQuery(queryNamesAndValues, file);

                return new URL(protocol, host, port, file.toString());
            } catch (MalformedURLException e) {
                throw new SafeIllegalArgumentException("Malformed URL", e);
            }
        }

        private static void encodePath(List<String> segments, StringBuilder result) {
            if (!segments.isEmpty()) {
                result.append('/');
            }
            PATH_JOINER.appendTo(result, segments);
        }

        private static void encodeQuery(Multimap<String, String> queryParams, StringBuilder result) {
            if (!queryParams.isEmpty()) {
                result.append('?');
            }
            QUERY_JOINER.appendTo(result, queryParams.entries());
        }

        private static String stripSlashes(String path) {
            if (path.isEmpty()) {
                return path;
            } else if (path.equals("/")) {
                return "";
            } else {
                int stripStart = path.startsWith("/") ? 1 : 0;
                int stripEnd = path.endsWith("/") ? 1 : 0;
                return path.substring(stripStart, path.length() - stripEnd);
            }
        }

        @Override
        public String toString() {
            return "UrlBuilderImpl{protocol='"
                    + protocol
                    + '\''
                    + ", host='"
                    + host
                    + '\''
                    + ", port="
                    + port
                    + ", pathSegments="
                    + pathSegments
                    + ", queryNamesAndValues="
                    + queryNamesAndValues
                    + '}';
        }
    }

    /** Encodes URL components per https://tools.ietf.org/html/rfc3986 . */
    @VisibleForTesting
    static class UrlEncoder {
        private static final CharMatcher DIGIT = CharMatcher.inRange('0', '9');
        private static final CharMatcher ALPHA = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'));
        private static final CharMatcher UNRESERVED = DIGIT.or(ALPHA).or(CharMatcher.anyOf("-._~"));
        private static final CharMatcher SUB_DELIMS = CharMatcher.anyOf("!$&'()*+,;=");
        private static final CharMatcher IS_HOST = UNRESERVED.or(SUB_DELIMS);
        private static final CharMatcher IS_P_CHAR = UNRESERVED.or(CharMatcher.anyOf(":@"));
        private static final CharMatcher IS_PATH = UNRESERVED.or(SUB_DELIMS).or(CharMatcher.anyOf("/"));
        // The RFC permits percent-encoding any character. We also percent encode sub-delimiters to avoid
        // incompatibilities with http specification beyond the general URI definition per
        // https://tools.ietf.org/html/rfc3986#section-3.3
        // > URI producing applications often use the reserved characters allowed in a segment to
        // > delimit scheme-specific or dereference-handler-specific subcomponents.
        private static final CharMatcher IS_QUERY_CHAR = IS_P_CHAR.or(CharMatcher.anyOf("/?"));

        static boolean isHost(String maybeHost) {
            return IS_HOST.matchesAllOf(maybeHost) || isIpv6Host(maybeHost);
        }

        static boolean isIpv6Host(String maybeHost) {
            int length = maybeHost.length();
            return length > 2
                    && maybeHost.codePointAt(0) == '['
                    && maybeHost.codePointAt(length - 1) == ']'
                    && InetAddresses.isInetAddress(maybeHost.substring(1, length - 1));
        }

        /**
         * Returns true if the given path contains only characters allowed in URL paths (including the segment
         * {@code /}.
         */
        static boolean isPath(String path) {
            return IS_PATH.matchesAllOf(path);
        }

        static String encodePathSegment(String pathComponent) {
            return encode(pathComponent, IS_P_CHAR);
        }

        static String encodeQueryNameOrValue(String nameOrValue) {
            return encode(nameOrValue, IS_QUERY_CHAR);
        }

        // percent-encodes every byte in the source string with it's percent-encoded representation, except for
        // bytes
        // that (in their unsigned char sense) are matched by charactersToKeep
        @VisibleForTesting
        static String encode(String source, CharMatcher charactersToKeep) {
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length()); // approx sizing
            boolean wasChanged = false;
            for (byte b : bytes) {
                if (charactersToKeep.matches(toChar(b))) {
                    bos.write(b);
                } else {
                    bos.write('%');
                    char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                    char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                    bos.write(hex1);
                    bos.write(hex2);
                    wasChanged = true;
                }
            }
            return wasChanged ? new String(bos.toByteArray(), StandardCharsets.UTF_8) : source;
        }

        // converts the given (signed) byte into an (unsigned) char
        private static char toChar(byte theByte) {
            if (theByte < 0) {
                return (char) (256 + theByte);
            } else {
                return (char) theByte;
            }
        }
    }
}
