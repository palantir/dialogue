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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.InetAddresses;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A simplistic URL builder, not tuned for performance. */
public final class UrlBuilder {

    private static final Joiner PATH_JOINER = Joiner.on('/');
    private static final Joiner.MapJoiner QUERY_JOINER = Joiner.on('&').withKeyValueSeparator('=');

    private final String protocol;
    private String host;
    private int port = -1;
    private List<String> pathSegments = new ArrayList<>();
    private Multimap<String, String> queryNamesAndValues = ArrayListMultimap.create();

    public static UrlBuilder http() {
        return new UrlBuilder("http");
    }

    public static UrlBuilder https() {
        return new UrlBuilder("https");
    }

    public static UrlBuilder withProtocol(String protocol) {
        return new UrlBuilder(protocol.toLowerCase());
    }

    public UrlBuilder newBuilder() {
        return new UrlBuilder(this);
    }

    private UrlBuilder(String protocol) {
        Preconditions.checkArgument(
                protocol.equals("http") || protocol.equals("https"),
                "unsupported protocol",
                SafeArg.of("protocol", protocol));
        this.protocol = protocol;
    }

    private UrlBuilder(UrlBuilder builder) {
        this.protocol = builder.protocol;
        this.host = builder.host;
        this.port = builder.port;
        this.pathSegments = new ArrayList<>(builder.pathSegments);
        this.queryNamesAndValues = ArrayListMultimap.create(builder.queryNamesAndValues);
    }

    /**
     * Accepts regular names (e.g., {@code google.com}), IPv4 addresses in dot notation (e.g., {@code 192.168.0.1}), and
     * IPv6 addresses of the form {@code [2010:836B:4179::836B:4179]} (note the enclosing square brackets).
     */
    public UrlBuilder host(String theHost) {
        Preconditions.checkArgument(UrlEncoder.isHost(theHost), "invalid host format", UnsafeArg.of("host", theHost));
        this.host = theHost;
        return this;
    }

    public UrlBuilder port(int thePort) {
        Preconditions.checkArgument(thePort >= 0 && thePort <= 65535, "port must be in range [0, 65535]");
        this.port = thePort;
        return this;
    }

    /**
     * Adds the given URL-encoded path segment (one or more) to the list of segments, or fails if the given segments
     * contain forbidden characters. Note that leading or trailing slashes are preserved, for instance {@code url
     * .pathSegment("foo").encodedPathSegments{"/bar/"}.pathSegment("baz")} yields a URL with path {@code foo//bar//baz}
     * (note the empty segments).
     */
    public UrlBuilder encodedPathSegments(String segments) {
        Preconditions.checkArgument(
                UrlEncoder.isPath(segments),
                "invalid characters in encoded path segments",
                UnsafeArg.of("segments", segments));
        this.pathSegments.add(segments);
        return this;
    }

    /** URL-encodes the given path segment and adds it to the list of segments. */
    public UrlBuilder pathSegment(String thePath) {
        this.pathSegments.add(UrlEncoder.encodePathSegment(thePath));
        return this;
    }

    /**
     * URL-encodes the given query parameter name and value and adds them to the list of query parameters. Note that no
     * guarantee is made regarding the ordering of query parameters in the resulting URL.
     */
    public UrlBuilder queryParam(String name, String value) {
        this.queryNamesAndValues.put(UrlEncoder.encodeQueryNameOrValue(name), UrlEncoder.encodeQueryNameOrValue(value));
        return this;
    }

    public URL build() {
        try {
            Preconditions.checkNotNull(protocol, "protocol must be set");
            Preconditions.checkNotNull(host, "host must be set");
            Preconditions.checkArgument(port != -1, "port must be set");

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

    /** Encodes URL components per https://tools.ietf.org/html/rfc3986 . */
    @VisibleForTesting
    static class UrlEncoder {
        private static final CharMatcher DIGIT = CharMatcher.inRange('0', '9');
        private static final CharMatcher ALPHA = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'));
        private static final CharMatcher UNRESERVED = DIGIT.or(ALPHA).or(CharMatcher.anyOf("-._~"));
        private static final CharMatcher SUB_DELIMS = CharMatcher.anyOf("!$&'()*+,;=");
        private static final CharMatcher IS_HOST = UNRESERVED.or(SUB_DELIMS);
        private static final CharMatcher IS_P_CHAR = UNRESERVED.or(SUB_DELIMS);
        private static final CharMatcher IS_PATH = UNRESERVED.or(SUB_DELIMS).or(CharMatcher.anyOf("/"));
        private static final CharMatcher IS_QUERY_CHAR =
                CharMatcher.anyOf("=&").negate().and(IS_P_CHAR.or(CharMatcher.anyOf("?/")));

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

        // percent-encodes every byte in the source string with it's percent-encoded representation, except for bytes
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
