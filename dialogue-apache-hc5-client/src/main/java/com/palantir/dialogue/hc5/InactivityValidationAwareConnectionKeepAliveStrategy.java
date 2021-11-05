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

import com.google.common.util.concurrent.RateLimiter;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * An {@link ConnectionKeepAliveStrategy} implementation based on the
 * {@link org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy} which
 * updates {@link PoolingHttpClientConnectionManager#setValidateAfterInactivity(TimeValue)}
 * based on server {@code Keep-Alive} response headers to avoid unnecessary checks when
 * the server advertises a persistent connection timeout.
 */
final class InactivityValidationAwareConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {
    private static final SafeLogger log =
            SafeLoggerFactory.get(InactivityValidationAwareConnectionKeepAliveStrategy.class);
    private static final String TIMEOUT_ELEMENT = "timeout";

    private final PoolingHttpClientConnectionManager connectionManager;
    private final String clientName;
    private final TimeValue defaultValidateAfterInactivity;
    private final RateLimiter loggingRateLimiter = RateLimiter.create(2);
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
        Iterator<HeaderElement> headerElementIterator = MessageSupport.iterate(response, HeaderElements.KEEP_ALIVE);
        while (headerElementIterator.hasNext()) {
            HeaderElement headerElement = headerElementIterator.next();
            String headerElementName = headerElement.getName();
            String headerElementValue = headerElement.getValue();
            if (headerElementValue != null && TIMEOUT_ELEMENT.equalsIgnoreCase(headerElementName)) {
                try {
                    long keepAliveTimeoutSeconds = Long.parseLong(headerElementValue);
                    if (keepAliveTimeoutSeconds > 0) {
                        TimeValue keepAliveValue = TimeValue.ofSeconds(keepAliveTimeoutSeconds);
                        updateInactivityValidationInterval(response.getCode(), keepAliveValue);
                        return keepAliveValue;
                    }
                } catch (NumberFormatException nfe) {
                    log.debug("invalid timeout value {}", SafeArg.of("timeoutValue", headerElementValue), nfe);
                }
            }
        }
        HttpClientContext clientContext = HttpClientContext.adapt(context);
        RequestConfig requestConfig = clientContext.getRequestConfig();
        updateInactivityValidationInterval(response.getCode(), defaultValidateAfterInactivity);
        return requestConfig.getConnectionKeepAlive();
    }

    private void updateInactivityValidationInterval(int statusCode, TimeValue newInterval) {
        // Only update values based on 2xx responses
        if (statusCode / 100 == 2) {
            TimeValue previousInterval = currentValidationInterval.getAndSet(newInterval);
            if (!Objects.equals(previousInterval, newInterval)) {
                // Rate limit in case of a server roll which changes the keep-alive value. Each line is printed
                // if the rate limiter isn't saturated, or if debug logging is enabled.
                if (loggingRateLimiter.tryAcquire() || log.isDebugEnabled()) {
                    log.info(
                            "Updating the validation interval for {} from {} to {}",
                            SafeArg.of("client", clientName),
                            SafeArg.of("previousInterval", previousInterval),
                            SafeArg.of("newInterval", newInterval));
                }
            }
            // Simple volatile write, no need to protect this in the getAndSet check. The getAndSet may race this call
            // so it's best to completely decouple the two.
            connectionManager.setValidateAfterInactivity(newInterval);
        }
    }
}
