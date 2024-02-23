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

import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.Safe;
import java.util.stream.Stream;

/** Internal interface to handle DNS refresh from various sources. */
interface DnsPollingSpec<INPUT> {

    /** Short name for observability describing the type of component polling for DNS updates. */
    @Safe
    String kind();

    Stream<String> extractUris(INPUT input);

    DnsPollingSpec<ServicesConfigBlock> RELOADING_FACTORY = new DnsPollingSpec<>() {

        @Override
        public String kind() {
            return "reloading-client-factory";
        }

        @Override
        public Stream<String> extractUris(ServicesConfigBlock servicesConfigBlock) {
            return servicesConfigBlock.services().values().stream().flatMap(psc -> psc.uris().stream());
        }
    };

    DnsPollingSpec<ClientConfiguration> CLIENT_CONFIG = new DnsPollingSpec<>() {

        @Override
        public String kind() {
            return "non-reloading-clientconfig";
        }

        @Override
        public Stream<String> extractUris(ClientConfiguration input) {
            return input.uris().stream();
        }
    };

    DnsPollingSpec<ServiceConfiguration> SERVICE_CONFIG = new DnsPollingSpec<>() {

        @Override
        public String kind() {
            return "non-reloading-serviceconfig";
        }

        @Override
        public Stream<String> extractUris(ServiceConfiguration input) {
            return input.uris().stream();
        }
    };
}
