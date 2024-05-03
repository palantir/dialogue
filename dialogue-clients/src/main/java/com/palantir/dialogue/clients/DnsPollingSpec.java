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

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.Safe;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Internal interface to handle DNS refresh from various sources. */
interface DnsPollingSpec<INPUT> {

    /** Short name for observability describing the type of component polling for DNS updates. */
    @Safe
    String kind();

    Stream<String> extractUris(INPUT input);

    /** Returns a channel-name associated with the host. */
    List<@Safe String> describeHostname(INPUT input, String hostname);

    static DnsPollingSpec<ClientConfiguration> clientConfig(@Safe String channelName) {
        return new DnsPollingSpec<>() {
            @Override
            public String kind() {
                return channelName;
            }

            @Override
            public Stream<String> extractUris(ClientConfiguration input) {
                return input.uris().stream();
            }

            @Override
            public List<@Safe String> describeHostname(ClientConfiguration _configuration, String _uri) {
                return ImmutableList.of(kind());
            }
        };
    }

    static DnsPollingSpec<ServiceConfiguration> serviceConfig(@Safe String channelName) {
        return new DnsPollingSpec<>() {
            @Override
            public String kind() {
                return channelName;
            }

            @Override
            public Stream<String> extractUris(ServiceConfiguration input) {
                return input.uris().stream();
            }

            @Override
            public List<@Safe String> describeHostname(ServiceConfiguration _configuration, String _uri) {
                return ImmutableList.of(kind());
            }
        };
    }

    static DnsPollingSpec<Optional<ServiceConfiguration>> optionalServiceConfig(@Safe String channelName) {
        return new DnsPollingSpec<>() {
            @Override
            public String kind() {
                return channelName;
            }

            @Override
            public Stream<String> extractUris(Optional<ServiceConfiguration> input) {
                return input.stream().flatMap(item -> item.uris().stream());
            }

            @Override
            public List<@Safe String> describeHostname(Optional<ServiceConfiguration> _configuration, String _uri) {
                return ImmutableList.of(kind());
            }
        };
    }
}
