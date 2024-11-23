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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.dialogue.core.TargetUri;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DnsSupportTest {

    private final TaggedMetricRegistry taggedMetrics = new DefaultTaggedMetricRegistry();

    @BeforeEach
    void before() {
        DnsSupport.invalidateCaches();
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
        assertThat(DnsSupport.tryParseUri(input)).satisfies(parsed -> {
            assertThat(parsed.isSuccessful()).isTrue();
            assertThat(parsed.uri()).isNotNull();
            assertThat(parsed.uri().getHost()).isEqualTo(expectedHostname);
            assertThat(parsed.exception()).isNull();
        });

        assertThat(DnsSupport.getTargetUris(
                        "test",
                        List.of(input),
                        DnsSupport.proxySelector(Optional.empty()),
                        Optional.empty(),
                        taggedMetrics))
                .hasSize(1)
                .containsExactly(TargetUri.of(URI.create(input).toString()));
    }

    @ParameterizedTest
    @CsvSource({
        "mesh-http://github.com/palantir/dialogue/",
        "mesh-https://github.com/palantir/dialogue/",
        "mesh-https://github.com:443/palantir/dialogue/",
        "mesh-https://github.com:8080/palantir/dialogue/",
    })
    void meshSchemeIsInvalid(String input) {
        assertThat(DnsSupport.tryParseUri(input)).satisfies(parsed -> {
            //            assertThat(parsed.isSuccessful()).isFalse();
            assertThat(parsed.uri()).isNull();
            assertThat(parsed.exception())
                    .isNotNull()
                    .isInstanceOf(SafeIllegalArgumentException.class)
                    .hasMessageContaining("foo");
        });
    }
}
