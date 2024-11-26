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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.core.BaseUrl;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpRequest;
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
        "https://www.example.com, www.example.com, -1,",
        "https://www.example.com:443, www.example.com, 443,",
        "https://www.example.com/path/to/foo/bar, www.example.com, -1,",
        "https://www.example.com/path/to/foo/bar?baz=quux&hello=world#hash-octothorpe, www.example.com, -1,",
        "https://user@www.example.com:8443/path/to/foo/bar?baz=quux&hello=world#hash-octothorpe ,"
                + " www.example.com, 8443, user",
    })
    void parseAuthority(String input, String expectedHost, int expectedPort, String expectedUserInfo) throws Exception {
        URL url = new URL(input);
        URI uri = URI.create(input);
        assertThat(ApacheHttpClientBlockingChannel.parseAuthority(url))
                .isEqualTo(ApacheHttpClientBlockingChannel.parseAuthority(uri.toURL()))
                .isEqualTo(ApacheHttpClientBlockingChannel.parseAuthority(new URL(uri.toString())))
                .satisfies(authority -> {
                    assertThat(authority.getHostName())
                            .isEqualTo(expectedHost)
                            .isEqualTo(uri.getHost())
                            .isEqualTo(url.getHost());
                    assertThat(authority.getPort())
                            .isEqualTo(expectedPort)
                            .isEqualTo(uri.getPort())
                            .isEqualTo(url.getPort());
                    assertThat(authority.getUserInfo())
                            .isEqualTo(expectedUserInfo)
                            .isEqualTo(uri.getUserInfo())
                            .isEqualTo(url.getUserInfo());
                });
    }
}
