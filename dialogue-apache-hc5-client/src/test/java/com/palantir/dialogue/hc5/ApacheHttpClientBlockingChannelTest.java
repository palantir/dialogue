/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ListMultimap;
import com.google.common.primitives.UnsignedBytes;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.core.BaseUrl;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApacheHttpClientBlockingChannelTest {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "POST"})
    void createRequest(String method) throws Exception {

        BaseUrl baseUrl = BaseUrl.of(new URL("https://www.example.com/foo/api"));
        Endpoint endpoint = new Endpoint() {
            private final PathTemplate pathTemplate = PathTemplate.builder()
                    .fixed("fixed")
                    .variable("endpoint")
                    .variable("index")
                    .build();

            @Override
            public HttpMethod httpMethod() {
                return HttpMethod.valueOf(method);
            }

            @Override
            public String serviceName() {
                return "testService";
            }

            @Override
            public String endpointName() {
                return "testEndpoint";
            }

            @Override
            public String version() {
                return "1.2.3";
            }

            @Override
            public void renderPath(ListMultimap<String, String> params, UrlBuilder url) {
                pathTemplate.fill(params, url);
            }
        };

        Request request = Request.builder()
                .pathParams(Map.of("fixed", "bar", "endpoint", "baz", "index", "42"))
                .putQueryParams("q", "1")
                .putQueryParams("test", "true")
                .build();

        ClassicHttpRequest httpRequest = ApacheHttpClientBlockingChannel.createRequest(baseUrl, endpoint, request);
        assertThat(httpRequest.getMethod()).isEqualTo(method);
        assertThat(httpRequest.getPath())
                .isEqualTo(httpRequest.getRequestUri())
                .isEqualTo("/foo/api/fixed/baz/42?q=1&test=true");
        assertThat(httpRequest.getUri())
                .isEqualTo(URI.create("https://www.example.com/foo/api/fixed/baz/42?q=1&test=true"));
    }

    @ParameterizedTest
    @CsvSource({
        "http://example.com, example.com, -1,",
        "https://example.com, example.com, -1,",
        "https://localhost:1234, localhost, 1234,",
        "https://127.0.0.1, 127.0.0.1, -1,",
        "https://[0:0:0:0:0:ffff:c0a8:0102], 0:0:0:0:0:ffff:c0a8:0102, -1,",
        "https://[0000:0000:0000:0000:0000:ffff:c0a8:0102], 0000:0000:0000:0000:0000:ffff:c0a8:0102, -1,",
        "https://[::1], ::1, -1,",
        "https://[::ffff:c0a8:102], ::ffff:c0a8:102, -1,",
        "https://127.0.0.1:1234, 127.0.0.1, 1234,",
        "https://[::1]:1234, ::1, 1234,",
        "https://www.example.com, www.example.com, -1,",
        "https://www.example.com:443, www.example.com, 443,",
        "https://www.example.com/path/to/foo/bar, www.example.com, -1,",
        "https://www.example.com/path/to/foo/bar?baz=quux&hello=world#hash-octothorpe, www.example.com, -1,",
        "https://user@www.example.com:8443/path/to/foo/bar?baz=quux&hello=world#hash-octothorpe ,"
                + " www.example.com, 8443, user",
        "https://user@[::1]:8443/path/to/foo/bar?baz=quux&hello=world#hash-octothorpe , ::1, 8443, user",
        "https://user@[0000:0000:0000:0000:0000:ffff:c0a8:0102]:8443/path/to/foo/bar?baz=quux&hello=world#an-octothorpe"
                + " , 0000:0000:0000:0000:0000:ffff:c0a8:0102, 8443, user",
        "https://user:slash%2Fslash@www.example.com, www.example.com, -1, user:slash%2Fslash",
    })
    void parseAuthority(String input, String expectedHost, int expectedPort, String expectedUserInfo) throws Exception {
        URL url = new URL(input);
        URI uri = URI.create(input);
        assertThat(url).isEqualTo(uri.toURL());
        assertThat(uri).isEqualTo(url.toURI());
        assertThat(ApacheHttpClientBlockingChannel.parseAuthority(url))
                .isEqualTo(ApacheHttpClientBlockingChannel.parseAuthority(uri.toURL()))
                .isEqualTo(ApacheHttpClientBlockingChannel.parseAuthority(new URL(uri.toString())))
                .isEqualTo(URIAuthority.create(uri.getRawAuthority()))
                .isEqualTo(URIAuthority.create(url.toURI().getRawAuthority()))
                .isEqualTo(URIAuthority.create(url.getAuthority()))
                .satisfies(authority -> {
                    assertThat(authority.getHostName())
                            .usingComparator(hostComparator)
                            .isEqualTo(expectedHost)
                            .isEqualTo(uri.getHost())
                            .isEqualTo(url.getHost());
                    assertThat(authority.getPort())
                            .isEqualTo(expectedPort)
                            .isEqualTo(uri.getPort())
                            .isEqualTo(url.getPort());
                    assertThat(authority.getUserInfo())
                            .usingComparator(userInfoComparator)
                            .isEqualTo(expectedUserInfo)
                            .isEqualTo(decode(uri.getUserInfo()))
                            .isEqualTo(decode(url.getUserInfo()));
                });
    }

    @Nullable
    private static String decode(String userInfo) {
        return userInfo == null ? null : URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
    }

    @Test
    void testHostComparator() {
        assertThat("www.example.com")
                .usingComparator(hostComparator)
                .isEqualTo("www.example.com")
                .isNotEqualTo("github.com");
        assertThat("127.0.0.1")
                .usingComparator(hostComparator)
                .isEqualTo("127.0.0.1")
                .isNotEqualTo("127.0.0.2");
        assertThat("::1")
                .usingComparator(hostComparator)
                .isEqualTo("::1")
                .isEqualTo("[::1]")
                .isEqualTo("[0000:0000:0000:0000:0000:0000:0000:0001]")
                .isNotEqualTo("[::2]")
                .isNotEqualTo("127.0.0.1");
        assertThat("::ffff:c0a8:102")
                .usingComparator(hostComparator)
                .isEqualTo("::ffff:c0a8:102")
                .isEqualTo("[0000:0000:0000:0000:0000:ffff:c0a8:0102]")
                .isNotEqualTo("::ffff:c0a8:101")
                .isNotEqualTo("[::ffff:c0a8:101]");
    }

    private static final Comparator<? super String> userInfoComparator = (info1, info2) -> {
        String u1 = decode(info1);
        String u2 = decode(info2);
        if (Objects.equals(u1, u2)) {
            return 0;
        }
        if (u1 == null) {
            return -1;
        }
        if (u2 == null) {
            return 1;
        }
        return u1.compareTo(u2);
    };

    private static final Comparator<? super String> hostComparator = (host1, host2) -> {
        if (host1.equals(host2)) {
            return 0;
        }
        // treat IPv6 addresses with and without brackets as equivalent
        InetAddress address1 = tryGetAddress(host1);
        InetAddress address2 = tryGetAddress(host2);
        if (address1 != null && address2 != null) {
            return UnsignedBytes.lexicographicalComparator().compare(address1.getAddress(), address2.getAddress());
        }
        return host1.compareTo(host2);
    };

    private static InetAddress tryGetAddress(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
