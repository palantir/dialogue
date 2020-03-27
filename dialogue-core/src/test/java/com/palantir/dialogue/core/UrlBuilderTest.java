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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.Test;

public final class UrlBuilderTest {

    @Test
    public void differentProtocols() throws Exception {
        assertThat(BaseUrl.DefaultUrlBuilder.from(new URL("http://host:80"))
                        .build()
                        .toString())
                .isEqualTo("http://host:80");
        assertThat(BaseUrl.DefaultUrlBuilder.from(new URL("https://host:80"))
                        .build()
                        .toString())
                .isEqualTo("https://host:80");
    }

    @Test
    public void populatesDefaultPort() throws Exception {
        assertThat(BaseUrl.DefaultUrlBuilder.from(new URL("http://host"))
                        .build()
                        .toString())
                .isEqualTo("http://host:80");
        assertThat(BaseUrl.DefaultUrlBuilder.from(new URL("https://host"))
                        .build()
                        .toString())
                .isEqualTo("https://host:443");
    }

    @Test
    public void validatesPort() {
        assertThatThrownBy(() -> BaseUrl.DefaultUrlBuilder.from(new URL("http://host:65536"))
                        .build())
                .hasMessage("port must be in range [0, 65535]");
    }

    @Test
    public void encodesPaths() throws Exception {
        assertThat(minimalUrl().pathSegment("foo").build().toString()).isEqualTo("http://host:80/foo");
        assertThat(minimalUrl().pathSegment("foo").pathSegment("bar").build().toString())
                .isEqualTo("http://host:80/foo/bar");
        assertThat(minimalUrl().pathSegment("foo/bar").build().toString()).isEqualTo("http://host:80/foo%2Fbar");
        assertThat(minimalUrl()
                        .pathSegment("!@#$%^&*()_+{}[]|\\|\"':;/?.>,<~`")
                        .build()
                        .toString())
                .isEqualTo("http://host:80/!%40%23$%25%5E&*()_+%7B%7D%5B%5D%7C%5C%7C%22'%3A;%2F%3F.%3E,%3C~%60");
    }

    @Test
    public void handlesEmptyPathSegments() throws Exception {
        assertThat(minimalUrl()
                        .pathSegment("")
                        .pathSegment("")
                        .pathSegment("bar")
                        .build()
                        .toString())
                .isEqualTo("http://host:80///bar");
    }

    @Test
    public void handlesPreEncodedPathSegments() throws Exception {
        assertThat(minimalUrl()
                        .encodedPathSegments("foo")
                        .pathSegment("baz")
                        .build()
                        .toString())
                .isEqualTo("http://host:80/foo/baz");
        assertThat(minimalUrl()
                        .encodedPathSegments("foo/bar")
                        .pathSegment("baz")
                        .build()
                        .toString())
                .isEqualTo("http://host:80/foo/bar/baz");
        assertThat(minimalUrl()
                        .encodedPathSegments("foo/")
                        .pathSegment("baz")
                        .build()
                        .toString())
                .isEqualTo("http://host:80/foo//baz");
        assertThat(minimalUrl()
                        .encodedPathSegments("/foo")
                        .pathSegment("baz")
                        .build()
                        .toString())
                .isEqualTo("http://host:80//foo/baz");
        assertThatThrownBy(() -> minimalUrl().encodedPathSegments("ö"))
                .hasMessage("invalid characters in encoded path segments: {segments=ö}");
    }

    @Test
    public void encodesQueryParams() throws Exception {
        assertThat(minimalUrl().queryParam("foo", "bar").build().toString()).isEqualTo("http://host:80?foo=bar");
        assertThat(minimalUrl().queryParam("question?&", "answer!&").build().toString())
                .isEqualTo("http://host:80?question?%26=answer!%26");
    }

    @Test
    public void encodesMultipleQueryParamsWithSameName() throws Exception {
        assertThat(minimalUrl()
                        .queryParam("foo", "bar")
                        .queryParam("foo", "baz")
                        .build()
                        .toString())
                .isEqualTo("http://host:80?foo=bar&foo=baz");
    }

    @Test
    public void fullExample() throws Exception {
        assertThat(BaseUrl.DefaultUrlBuilder.from(new URL("https://host:80"))
                        .pathSegment("foo")
                        .pathSegment("bar")
                        .queryParam("boom", "baz")
                        .queryParam("question", "answer")
                        .build()
                        .toString())
                .isEqualTo("https://host:80/foo/bar?question=answer&boom=baz");
    }

    @Test
    public void urlEncoder_isHost_acceptsHostsPerRfc() {
        assertThat(BaseUrl.UrlEncoder.isHost("aAzZ09!$&'()*+,;=")).isTrue();
        assertThat(BaseUrl.UrlEncoder.isHost("192.168.0.1")).isTrue();
        assertThat(BaseUrl.UrlEncoder.isHost("[2010:836B:4179::836B:4179]")).isTrue();

        assertThat(BaseUrl.UrlEncoder.isHost("ö")).isFalse();
        assertThat(BaseUrl.UrlEncoder.isHost("#")).isFalse();
        assertThat(BaseUrl.UrlEncoder.isHost("@")).isFalse();
        assertThat(BaseUrl.UrlEncoder.isHost("2010:836B:4179::836B:4179")).isFalse();
    }

    @Test
    public void urlEncoder_encodePathSegment_onlyEncodesNonReservedChars() {
        String nonReserved = "aAzZ09!$&'()*+,;=";
        assertThat(BaseUrl.UrlEncoder.encodePathSegment(nonReserved)).isEqualTo(nonReserved);
        assertThat(BaseUrl.UrlEncoder.encodePathSegment("/")).isEqualTo("%2F");
    }

    @Test
    public void urlEncoder_encodeQuery_onlyEncodesNonReservedChars() {
        String nonReserved = "aAzZ09!$'()*,;/?";
        assertThat(BaseUrl.UrlEncoder.encodeQueryNameOrValue(nonReserved)).isEqualTo(nonReserved);
        assertThat(BaseUrl.UrlEncoder.encodeQueryNameOrValue("@[]{}ßçö"))
                .isEqualTo("%40%5B%5D%7B%7D%C3%9F%C3%A7%C3%B6");
        assertThat(BaseUrl.UrlEncoder.encodeQueryNameOrValue("=&+")).isEqualTo("%3D%26%2B");
    }

    @Test
    public void newBuilderCopiesAllFields() throws Exception {
        BaseUrl.DefaultUrlBuilder original = BaseUrl.DefaultUrlBuilder.from(new URL("http://foo:42"))
                .pathSegment("foo")
                .queryParam("name", "value");
        BaseUrl.DefaultUrlBuilder copy = original.newBuilder();
        original.pathSegment("foo-new").queryParam("name-new", "value-new");
        assertThat(copy.build().toString()).isEqualTo("http://foo:42/foo?name=value");
    }

    private static BaseUrl.DefaultUrlBuilder minimalUrl() throws MalformedURLException {
        return BaseUrl.DefaultUrlBuilder.from(new URL("http://host:80"));
    }
}
