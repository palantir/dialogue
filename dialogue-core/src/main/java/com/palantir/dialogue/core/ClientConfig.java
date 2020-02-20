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

package com.palantir.dialogue.core;

import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.Preconditions;
import java.util.List;

/**
 * Configuration specifying everything necessary to talk to an abstract upstream 'service', with a number of
 * possible uris. Intended to give us the flexibility to not require the legacy conjure-java-runtime jars at some
 * point in the future.
 *
 * All getters package private initially.
 */
@SuppressWarnings("VisibilityModifier") // TODO(dfox): switch to getters when we can be bothered
public final class ClientConfig {

    final ClientConfiguration legacyClientConfiguration;
    final HttpClientType httpClientType;
    final UserAgent userAgent;

    private ClientConfig(Builder builder) {
        this.legacyClientConfiguration =
                Preconditions.checkNotNull(builder.legacyClientConfiguration, "legacyClientConfiguration");
        this.httpClientType = Preconditions.checkNotNull(builder.httpClientType, "rawClientType");
        this.userAgent = Preconditions.checkNotNull(builder.userAgent, "userAgent");
    }

    List<String> uris() {
        return legacyClientConfiguration.uris();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ClientConfiguration legacyClientConfiguration;
        private HttpClientType httpClientType;
        private UserAgent userAgent;

        /** this method exists for backcompat reasons. */
        public Builder from(ClientConfiguration cjrClientConfig) {
            this.legacyClientConfiguration = cjrClientConfig;
            return this;
        }

        public Builder rawClientType(HttpClientType rawType) {
            this.httpClientType = rawType;
            return this;
        }

        public Builder userAgent(UserAgent value) {
            this.userAgent = value;
            return this;
        }

        public ClientConfig build() {
            return new ClientConfig(this);
        }
    }

    public enum HttpClientType {
        APACHE,
        OKHTTP,
        HTTP_URL_CONNECTION,
        JAVA9_HTTPCLIENT
    }
}
