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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class HttpUrlTest {

    @Test
    public void differentProtocols() {
        assertThat(HttpUrl.http().host("host").port(80).build().toUrl().toString()).isEqualTo("http://host:80");
        assertThat(HttpUrl.https().host("host").port(80).build().toUrl().toString()).isEqualTo("https://host:80");
    }

    @Test
    public void differentHosts() {
        assertThat(HttpUrl.http().host("host").port(80).build().toUrl().toString()).isEqualTo("http://host:80");
        assertThat(HttpUrl.http().host("another-host").port(80).build().toUrl().toString())
                .isEqualTo("http://another-host:80");
        assertThatThrownBy(() -> HttpUrl.http().build()).hasMessage("host must be set");
    }

    @Test
    public void differentPorts() {
        assertThat(HttpUrl.http().host("host").port(80).build().toUrl().toString()).isEqualTo("http://host:80");
        assertThat(HttpUrl.http().host("host").port(8080).build().toUrl().toString()).isEqualTo("http://host:8080");
        assertThatThrownBy(() -> HttpUrl.http().host("host").build())
                .hasMessage("port must be set");
    }

    @Test
    public void encodesPaths() {
        assertThat(minimalUrl().pathSegment("foo").build().toUrl().toString())
                .isEqualTo("http://host:80/foo");
        assertThat(minimalUrl().pathSegment("foo").pathSegment("bar").build().toUrl().toString())
                .isEqualTo("http://host:80/foo/bar");
        assertThat(minimalUrl().pathSegment("foo/bar").build().toUrl().toString())
                .isEqualTo("http://host:80/foo%2Fbar");
        assertThat(minimalUrl().pathSegment("!@#$%^&*()_+{}[]|\\|\"':;/?.>,<~`").build().toUrl().toString())
                .isEqualTo("http://host:80/!%40%23$%25%5E&*()_+%7B%7D%5B%5D%7C%5C%7C%22'%3A;%2F%3F.%3E,%3C~%60");
    }

    @Test
    public void encodesQueryParams() {
        assertThat(minimalUrl().queryParam("foo", "bar").build().toUrl().toString())
                .isEqualTo("http://host:80?foo=bar");
        assertThat(minimalUrl().queryParam("question?&", "answer!&").build().toUrl().toString())
                .isEqualTo("http://host:80?question?%26=answer!%26");
    }

    @Test
    public void encodesMultipleQueryParamsWithSameName() {
        assertThat(minimalUrl().queryParam("foo", "bar").queryParam("foo", "baz").build().toUrl().toString())
                .isEqualTo("http://host:80?foo=bar&foo=baz");
    }

    @Test
    public void fullExample() {
        assertThat(HttpUrl.https()
                .host("host")
                .port(80)
                .pathSegment("foo")
                .pathSegment("bar")
                .queryParam("boom", "baz")
                .queryParam("question", "answer")
                .build().toUrl().toString())
                .isEqualTo("https://host:80/foo/bar?boom=baz&question=answer");
    }

    @Test
    public void urlEncoder_isHost_acceptsHostsPerRfc() {
        assertThat(HttpUrl.UrlEncoder.isHost("aAzZ09!$&'()*+,;=")).isTrue();
        assertThat(HttpUrl.UrlEncoder.isHost("192.168.0.1")).isTrue();
        assertThat(HttpUrl.UrlEncoder.isHost("[2010:836B:4179::836B:4179]")).isTrue();

        assertThat(HttpUrl.UrlEncoder.isHost("ö")).isFalse();
        assertThat(HttpUrl.UrlEncoder.isHost("#")).isFalse();
        assertThat(HttpUrl.UrlEncoder.isHost("@")).isFalse();
        assertThat(HttpUrl.UrlEncoder.isHost("2010:836B:4179::836B:4179")).isFalse();
    }

    @Test
    public void urlEncoder_encodePathSegment_onlyEncodesNonReservedChars() {
        String nonReserved = "aAzZ09!$&'()*+,;=";
        assertThat(HttpUrl.UrlEncoder.encodePathSegment(nonReserved)).isEqualTo(nonReserved);
        assertThat(HttpUrl.UrlEncoder.encodePathSegment("/")).isEqualTo("%2F");
    }

    @Test
    public void urlEncoder_encodeQuery_onlyEncodesNonReservedChars() {
        String nonReserved = "aAzZ09!$'()*+,;?/";
        assertThat(HttpUrl.UrlEncoder.encodeQueryNameOrValue(nonReserved)).isEqualTo(nonReserved);
        assertThat(HttpUrl.UrlEncoder.encodeQueryNameOrValue("@[]{}ßçö"))
                .isEqualTo("%40%5B%5D%7B%7D%C3%9F%C3%A7%C3%B6");
        assertThat(HttpUrl.UrlEncoder.encodeQueryNameOrValue("&=")).isEqualTo("%26%3D");
    }

    private static HttpUrl.Builder minimalUrl() {
        return HttpUrl.http().host("host").port(80);
    }
}
