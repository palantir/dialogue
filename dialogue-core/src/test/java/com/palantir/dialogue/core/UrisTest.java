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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrisTest {

    @BeforeEach
    void before() {
        Uris.clearCache();
    }

    @AfterEach
    void after() {
        Uris.clearCache();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://www.palantir.com/",
                "https://www.palantir.com",
                "https://github.com/palantir",
                "https://www.example.com/foo/bar/baz",
            })
    void parsesValidUris(String input) {
        assertThat(Uris.tryParse(input)).isNotNull().satisfies(parsed -> {
            assertThat(parsed.isSuccessful()).isTrue();
            assertThat(parsed.exception()).isNull();
            assertThat(parsed.uri()).isNotNull();
            assertThat(parsed.uriOrThrow()).isNotNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {" x ", " ", "foobar://"})
    void parsesInvalidUris(String input) {
        assertThat(Uris.tryParse(input)).isNotNull().satisfies(parsed -> {
            assertThat(parsed.isSuccessful()).isFalse();
            assertThat(parsed.exception()).isNotNull();
            assertThat(parsed.uri()).isNull();
            assertThatThrownBy(parsed::uriOrThrow)
                    .isInstanceOf(SafeIllegalArgumentException.class)
                    .hasMessageContaining("Failed to parse URI");
        });
    }

    @ParameterizedTest
    @CsvSource({
        "github.com,https://github.com",
        "github.com,https://github.com:443",
        "github.com,https://github.com:8080",
        "github.com,https://github.com/palantir/dialogue/",
        "github.com,https://github.com:443/palantir/dialogue/",
        "github.com,https://github.com:8080/palantir/dialogue/",
    })
    void tryGetHost(String expectedHostname, String input) {
        assertThat(Uris.tryParse(input)).satisfies(parsed -> {
            assertThat(parsed.isSuccessful()).isTrue();
            assertThat(parsed.uri()).isNotNull();
            assertThat(parsed.uri().getHost()).isEqualTo(expectedHostname);
            assertThat(parsed.exception()).isNull();
        });
    }
}
