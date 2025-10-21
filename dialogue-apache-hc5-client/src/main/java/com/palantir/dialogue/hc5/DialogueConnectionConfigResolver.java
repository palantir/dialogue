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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

final class DialogueConnectionConfigResolver implements Resolver<HttpRoute, ConnectionConfig> {

    // Increased from two seconds to four seconds because we have strong support for retries
    // and can optimistically avoid expensive connection checks. Failures caused by NoHttpResponseExceptions
    // are possible when the target closes connections prior to this timeout, and can be safely retried.
    // Ideally this value would be larger for RPC, however some servers use relatively low defaults:
    // apache httpd versions 1.3 and 2.0: 15 seconds:
    // https://httpd.apache.org/docs/2.0/mod/core.html#keepalivetimeout
    // apache httpd version 2.2 and above: 5 seconds
    // https://httpd.apache.org/docs/2.2/mod/core.html#keepalivetimeout
    // nodejs http server: 5 seconds
    // https://nodejs.org/api/http.html#http_server_keepalivetimeout
    // nginx: 75 seconds (good)
    // https://nginx.org/en/docs/http/ngx_http_core_module.html#keepalive_timeout
    // dropwizard: 30 seconds (see idleTimeout in the linked docs)
    // https://www.dropwizard.io/en/latest/manual/configuration.html#Connectors
    // wc: 60 seconds (internal)
    private static final TimeValue CONNECTION_INACTIVITY_CHECK = TimeValue.ofMilliseconds(
            Integer.getInteger("dialogue.experimental.inactivity.check.threshold.millis", 4_000));

    private final Timeout connectTimeout;
    private final Timeout socketTimeout;

    // We create a new connectionConfig when the connectionInactivityCheck interval changes
    // to avoid allocating a new ConnectionConfig each time the value is queried.
    private volatile ConnectionConfig connectionConfig;

    DialogueConnectionConfigResolver(Timeout connectTimeout, Timeout socketTimeout) {
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        setValidateAfterInactivity(CONNECTION_INACTIVITY_CHECK);
    }

    void setValidateAfterInactivity(TimeValue connectionInactivityCheck) {
        connectionConfig = ConnectionConfig.custom()
                .setValidateAfterInactivity(connectionInactivityCheck)
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout)
                .build();
    }

    TimeValue getValidateAfterInactivity() {
        return connectionConfig.getValidateAfterInactivity();
    }

    @Override
    public ConnectionConfig resolve(HttpRoute _ignored) {
        return connectionConfig;
    }
}
