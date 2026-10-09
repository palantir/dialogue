/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeadlineFailureInjectionConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "false", "FALSE"})
    void disabled_when_value_is_absent_or_false(String value) {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(value))
                .isEmpty();
    }

    @Test
    void disabled_when_variable_is_unset() {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(null))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", " true "})
    void true_enables_injection_with_both_defaults(String value) {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(value))
                .hasValueSatisfying(config -> {
                    assertThat(config.oneInEvery())
                            .isEqualTo(DeadlineFailureInjectionConfiguration.DEFAULT_ONE_IN_EVERY);
                    assertThat(config.minimumFailureInterval())
                            .isEqualTo(DeadlineFailureInjectionConfiguration.DEFAULT_MINIMUM_FAILURE_INTERVAL);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"500", " 500 ", "1/500", "1/ 500"})
    void a_rate_enables_injection_and_keeps_the_default_interval(String value) {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(value))
                .hasValueSatisfying(config -> {
                    assertThat(config.oneInEvery()).isEqualTo(500);
                    assertThat(config.minimumFailureInterval())
                            .isEqualTo(DeadlineFailureInjectionConfiguration.DEFAULT_MINIMUM_FAILURE_INTERVAL);
                });
    }

    @Test
    void a_rate_of_one_selects_every_request() {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue("1"))
                .hasValueSatisfying(config -> assertThat(config.oneInEvery()).isEqualTo(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"500,1m", "500, 1m", "500,PT1M", "500,60s", "500,1M"})
    void an_interval_may_be_supplied_alongside_the_rate(String value) {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(value))
                .hasValueSatisfying(config -> {
                    assertThat(config.oneInEvery()).isEqualTo(500);
                    assertThat(config.minimumFailureInterval()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    void a_zero_interval_removes_the_cap() {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue("1,0"))
                .hasValueSatisfying(config -> {
                    assertThat(config.oneInEvery()).isEqualTo(1);
                    assertThat(config.minimumFailureInterval()).isZero();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1/0", "banana", "1.5", "1/", "100_000"})
    void invalid_rates_leave_injection_disabled(String value) {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(value))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"500,", "500,   ", "500,banana", "500,-1m", "500,1x", "500,1m,2m"})
    void invalid_intervals_leave_injection_disabled(String value) {
        assertThat(DeadlineFailureInjectionConfiguration.fromEnvironmentValue(value))
                .isEmpty();
    }
}
