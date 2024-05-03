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
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.Unsafe;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Internal interface to handle DNS refresh from various sources. */
interface DnsPollingSpec<INPUT> {

    /** Short name for observability describing the type of component polling for DNS updates. */
    @Safe
    String kind();

    Stream<String> extractUris(INPUT input);

    /** Returns a channel-name associated with the host. */
    List<@Safe String> describeHostname(INPUT input, String hostname);

    DnsPollingSpec<ServicesConfigBlock> RELOADING_FACTORY = new DnsPollingSpec<>() {

        @Override
        public String kind() {
            return "reloading-client-factory";
        }

        @Override
        public Stream<String> extractUris(ServicesConfigBlock servicesConfigBlock) {
            return servicesConfigBlock.services().values().stream().flatMap(psc -> psc.uris().stream());
        }

        @Override
        public List<@Safe String> describeHostname(ServicesConfigBlock servicesConfigBlock, @Unsafe String host) {
            return servicesConfigBlock.services().entrySet().stream()
                    .filter(entry -> entry.getValue().uris().stream()
                            .map(DnsSupport::tryGetHost)
                            .filter(Objects::nonNull)
                            .anyMatch(host::equals))
                    .map(Map.Entry::getKey)
                    .map(ChannelNames::reloading)
                    .collect(ImmutableList.toImmutableList());
        }
    };

    static DnsPollingSpec<ClientConfiguration> clientConfig(@Safe String channelName) {
        return new ClientConfigurationDnsPollingSpec(channelName);
    }

    static DnsPollingSpec<ServiceConfiguration> serviceConfig(@Safe String channelName) {
        return new ServiceConfigurationDnsPollingSpec(channelName);
    }

    final class ClientConfigurationDnsPollingSpec implements DnsPollingSpec<ClientConfiguration> {

        private final @Safe String channelName;

        ClientConfigurationDnsPollingSpec(@Safe String channelName) {
            this.channelName = channelName;
        }

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

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }

            ClientConfigurationDnsPollingSpec that = (ClientConfigurationDnsPollingSpec) other;
            return channelName.equals(that.channelName);
        }

        @Override
        public int hashCode() {
            return channelName.hashCode();
        }
    }

    final class ServiceConfigurationDnsPollingSpec implements DnsPollingSpec<ServiceConfiguration> {

        private final @Safe String channelName;

        ServiceConfigurationDnsPollingSpec(@Safe String channelName) {
            this.channelName = channelName;
        }

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

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }

            ServiceConfigurationDnsPollingSpec that = (ServiceConfigurationDnsPollingSpec) other;
            return channelName.equals(that.channelName);
        }

        @Override
        public int hashCode() {
            return channelName.hashCode();
        }
    }
}
