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

import com.palantir.logsafe.SafeArg;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InactivityValidationAwareConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {
    private static final Logger log =
            LoggerFactory.getLogger(InactivityValidationAwareConnectionKeepAliveStrategy.class);

    private static final ConnectionKeepAliveStrategy DELEGATE = DefaultConnectionKeepAliveStrategy.INSTANCE;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final String clientName;
    private final TimeValue defaultValidateAfterInactivity;
    /**
     * This field is used for observability. It's possible, though unlikely, that the value can get out of sync
     * with the connection manager in some scenarios.
     */
    private final AtomicReference<TimeValue> currentValidationInterval;

    InactivityValidationAwareConnectionKeepAliveStrategy(
            PoolingHttpClientConnectionManager connectionManager, String clientName) {
        this.connectionManager = connectionManager;
        this.clientName = clientName;
        // Store the initial inactivity interval to restore if responses re received without
        // keep-alive headers.
        this.defaultValidateAfterInactivity = connectionManager.getValidateAfterInactivity();
        this.currentValidationInterval = new AtomicReference<>(defaultValidateAfterInactivity);
    }

    @Override
    public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
        TimeValue result = DELEGATE.getKeepAliveDuration(response, context);
        if (result != null
                // Only use keep-alive values from 2xx responses
                && response.getCode() / 100 == 2) {
            TimeValue newInterval =
                    containsKeepAliveHeaderWithTimeout(response) ? result : defaultValidateAfterInactivity;
            TimeValue previousInterval = currentValidationInterval.getAndSet(newInterval);
            if (!Objects.equals(previousInterval, newInterval)) {
                log.info(
                        "Updating the validation interval for {} from {} to {}",
                        SafeArg.of("client", clientName),
                        SafeArg.of("previousInterval", previousInterval),
                        SafeArg.of("newInterval", newInterval));
            }
            // Simple volatile write, no need to protect this in the getAndSet check. The getAndSet may race this call
            // so it's best to completely decouple the two.
            connectionManager.setValidateAfterInactivity(newInterval);
        }
        return result;
    }

    private static boolean containsKeepAliveHeaderWithTimeout(HttpResponse response) {
        Header keepAlive = response.getFirstHeader(HeaderElements.KEEP_ALIVE);
        if (keepAlive != null) {
            String keepAliveValue = keepAlive.getValue();
            return keepAliveValue != null && keepAliveValue.contains("timeout=");
        }
        return false;
    }
}
