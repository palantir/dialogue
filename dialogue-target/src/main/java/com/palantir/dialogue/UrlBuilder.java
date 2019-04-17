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
import com.google.common.collect.Iterables;
import com.google.common.net.InetAddresses;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A simplistic URL builder, not tuned for performance. */
public final class UrlBuilder {

    private static final Joiner SLASH_JOINER = Joiner.on('/');

    private final String protocol;
    private String host;
    private int port = -1;
    private List<String> pathSegments = new ArrayList<>();
    private List<String> queryNamesAndValues = new ArrayList<>(); // alternating (name, value) pairs

    public static UrlBuilder http() {
        return new UrlBuilder("http");
    }

    public static UrlBuilder https() {
        return new UrlBuilder("https");
    }

    private UrlBuilder(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Accepts regular names (e.g., {@code google.com}), IPv4 addresses in dot notation (e.g.,
     * {@code 192.168.0.1}), and IPv6 addresses of the form
     * {@code [2010:836B:4179::836B:4179]} (note the enclosing square brackets).
     */
    public UrlBuilder host(String theHost) {
        this.host = theHost;
        return this;
    }

    public UrlBuilder port(int thePort) {
        this.port = thePort;
        return this;
    }

    /** URL-encodes the given path segment and adds it to the list of segments. */
    public UrlBuilder pathSegment(String thePath) {
        this.pathSegments.add(thePath);
        return this;
    }

    /** URL-encodes the given query parameter name and value and adds them to the list of query parameters. */
    public UrlBuilder queryParam(String name, String value) {
        this.queryNamesAndValues.add(name);
        this.queryNamesAndValues.add(value);
        return this;
    }

    public URL build() {
        try {
            Preconditions.checkNotNull(protocol, "protocol must be set");
            Preconditions.checkNotNull(host, "host must be set");
            Preconditions.checkArgument(UrlEncoder.isHost(host), "invalid host format", UnsafeArg.of("host", host));
            Preconditions.checkArgument(port != -1, "port must be set");
            Preconditions.checkArgument(port >= 0 && port <= 65535, "port must be in range [0, 65535]");

            StringBuilder file = new StringBuilder();
            encodePath(pathSegments, file);
            encodeQuery(queryNamesAndValues, file);

            return new URL(protocol, host, port, file.toString());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL", e);
        }
    }

    private static void encodePath(List<String> segments, StringBuilder result) {
        if (!segments.isEmpty()) {
            result.append('/');
        }
        SLASH_JOINER.appendTo(result, Iterables.transform(segments, UrlEncoder::encodePathSegment));
    }

    private static void encodeQuery(List<String> pairs, StringBuilder result) {
        if (!pairs.isEmpty()) {
            result.append('?');
        }
        for (int i = 0; i < pairs.size(); i += 2) {
            result.append(UrlEncoder.encodeQueryNameOrValue(pairs.get(i)));
            result.append('=');
            result.append(UrlEncoder.encodeQueryNameOrValue(pairs.get(i + 1)));
            if (i < pairs.size() - 2) {
                result.append('&');
            }
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
        private static final CharMatcher IS_P_CHAR = UNRESERVED.or(SUB_DELIMS);
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
            ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length());  // approx sizing
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
            return wasChanged
                    ? new String(bos.toByteArray(), StandardCharsets.UTF_8)
                    : source;
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
