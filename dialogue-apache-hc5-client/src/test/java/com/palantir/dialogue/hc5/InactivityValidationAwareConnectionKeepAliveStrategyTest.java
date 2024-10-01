/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InactivityValidationAwareConnectionKeepAliveStrategyTest {

    private static final HttpClientContext CONTEXT = new HttpClientContext();
    private static final TimeValue INITIAL_TIMEOUT = TimeValue.ofSeconds(5);

    private final DialogueConnectionConfigResolver resolver =
            new DialogueConnectionConfigResolver(Timeout.ofSeconds(2), Timeout.ofSeconds(10));

    @BeforeEach
    void beforeEach() {
        resolver.setValidateAfterInactivity(INITIAL_TIMEOUT);
    }

    private TimeValue getResolverInactivityInterval() {
        return resolver.resolve(null).getValidateAfterInactivity();
    }

    @Test
    void testNoKeepAliveHeader() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(InactivityValidationAwareConnectionKeepAliveStrategy.IDLE_CONNECTION_TIMEOUT);

        assertThat(getResolverInactivityInterval()).isEqualTo(INITIAL_TIMEOUT);
    }

    @Test
    void testKeepAliveHeaderWithTimeout() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "timeout=60");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        TimeValue expected = TimeValue.ofSeconds(60);
        assertThat(value).isEqualTo(expected);
        assertThat(getResolverInactivityInterval()).isEqualTo(expected);
    }

    @Test
    void testKeepAliveHeaderWithTimeoutAndMax() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "timeout=60, max=10");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        TimeValue expected = TimeValue.ofSeconds(60);
        assertThat(value).isEqualTo(expected);
        assertThat(getResolverInactivityInterval()).isEqualTo(expected);
    }

    @Test
    void testKeepAliveHeaderWithTimeoutIgnoredNon2xx() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(500);
        response.addHeader("Keep-Alive", "timeout=60");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(TimeValue.ofSeconds(60));
        assertThat(getResolverInactivityInterval()).isEqualTo(INITIAL_TIMEOUT);
    }

    @Test
    void testKeepAliveHeaderWithoutTimeout() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "max=60");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(InactivityValidationAwareConnectionKeepAliveStrategy.IDLE_CONNECTION_TIMEOUT);
        assertThat(getResolverInactivityInterval()).isEqualTo(INITIAL_TIMEOUT);
    }

    @Test
    void testKeepAliveHeaderWithZeroTimeout() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "timeout=0");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(InactivityValidationAwareConnectionKeepAliveStrategy.IDLE_CONNECTION_TIMEOUT);
        assertThat(getResolverInactivityInterval()).isEqualTo(INITIAL_TIMEOUT);
    }

    @Test
    void testKeepAliveHeaderWithNegativeTimeout() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(resolver, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "timeout=-1");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(InactivityValidationAwareConnectionKeepAliveStrategy.IDLE_CONNECTION_TIMEOUT);
        assertThat(getResolverInactivityInterval()).isEqualTo(INITIAL_TIMEOUT);
    }
}
