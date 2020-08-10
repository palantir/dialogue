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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InactivityValidationAwareConnectionKeepAliveStrategyTest {

    private static final HttpClientContext CONTEXT = new HttpClientContext();
    private static final TimeValue INITIAL_TIMEOUT = TimeValue.ofSeconds(5);

    @Mock
    private PoolingHttpClientConnectionManager manager;

    @BeforeEach
    void beforeEach() {
        when(manager.getValidateAfterInactivity()).thenReturn(INITIAL_TIMEOUT);
    }

    @Test
    void testNoKeepAliveHeader() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(manager, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(CONTEXT.getRequestConfig().getConnectionKeepAlive());
        verify(manager).setValidateAfterInactivity(eq(INITIAL_TIMEOUT));
    }

    @Test
    void testKeepAliveHeaderWithTimeout() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(manager, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "timeout=60");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        TimeValue expected = TimeValue.ofSeconds(60);
        assertThat(value).isEqualTo(expected);
        verify(manager).setValidateAfterInactivity(eq(expected));
    }

    @Test
    void testKeepAliveHeaderWithTimeoutIgnoredNon2xx() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(manager, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(500);
        response.addHeader("Keep-Alive", "timeout=60");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(TimeValue.ofSeconds(60));
        verify(manager, never()).setValidateAfterInactivity(any());
    }

    @Test
    void testKeepAliveHeaderWithoutTimeout() {
        InactivityValidationAwareConnectionKeepAliveStrategy strategy =
                new InactivityValidationAwareConnectionKeepAliveStrategy(manager, "name");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.addHeader("Keep-Alive", "max=60");
        TimeValue value = strategy.getKeepAliveDuration(response, CONTEXT);
        assertThat(value).isEqualTo(CONTEXT.getRequestConfig().getConnectionKeepAlive());
        verify(manager).setValidateAfterInactivity(eq(INITIAL_TIMEOUT));
    }
}
