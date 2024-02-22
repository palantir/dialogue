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
import com.google.common.collect.ImmutableSetMultimap;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.logsafe.Safe;
import java.net.InetAddress;
import java.net.URI;
import java.util.stream.Stream;

/** Internal interface to handle DNS refresh from various sources. */
interface DnsPollingSpec<INPUT, OUTPUT> {

    /** Short name for observability describing the type of component polling for DNS updates. */
    @Safe
    String kind();

    Stream<String> extractUris(INPUT input);

    OUTPUT createOutput(INPUT input, ImmutableSetMultimap<String, InetAddress> resolvedHosts);

    DnsPollingSpec<ServicesConfigBlock, ServicesConfigBlockWithResolvedHosts> RELOADING_FACTORY =
            new DnsPollingSpec<>() {

                @Override
                public String kind() {
                    return "reloading-client-factory";
                }

                @Override
                public Stream<String> extractUris(ServicesConfigBlock servicesConfigBlock) {
                    return servicesConfigBlock.services().values().stream().flatMap(psc -> psc.uris().stream());
                }

                @Override
                public ServicesConfigBlockWithResolvedHosts createOutput(
                        ServicesConfigBlock servicesConfigBlock,
                        ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
                    return ImmutableServicesConfigBlockWithResolvedHosts.of(servicesConfigBlock, resolvedHosts);
                }
            };

    DnsPollingSpec<ClientConfiguration, ClientConfigurationWithTargets> CLIENT_CONFIG = new DnsPollingSpec<>() {

        @Override
        public String kind() {
            return "non-reloading-clientconfig";
        }

        @Override
        public Stream<String> extractUris(ClientConfiguration input) {
            return input.uris().stream();
        }

        @Override
        public ClientConfigurationWithTargets createOutput(
                ClientConfiguration input, ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
            // TODO(dns): proxy and mesh uri detection
            ImmutableList.Builder<TargetUri> targets = ImmutableList.builder();
            for (String uri : input.uris()) {
                // Failure may throw here because the input isn't actually refreshable
                URI parsed = URI.create(uri);
                for (InetAddress address : resolvedHosts.get(parsed.getHost())) {
                    targets.add(TargetUri.builder()
                            .uri(uri)
                            .resolvedAddress(address)
                            .build());
                }
            }
            return ImmutableClientConfigurationWithTargets.of(input, targets.build());
        }
    };

    DnsPollingSpec<ServiceConfiguration, ServiceConfigurationWithTargets> SERVICE_CONFIG = new DnsPollingSpec<>() {

        @Override
        public String kind() {
            return "non-reloading-serviceconfig";
        }

        @Override
        public Stream<String> extractUris(ServiceConfiguration input) {
            return input.uris().stream();
        }

        @Override
        public ServiceConfigurationWithTargets createOutput(
                ServiceConfiguration input, ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
            // TODO(dns): proxy and mesh uri detection
            ImmutableList.Builder<TargetUri> targets = ImmutableList.builder();
            for (String uri : input.uris()) {
                // Failure may throw here because the input isn't actually refreshable
                URI parsed = URI.create(uri);
                for (InetAddress address : resolvedHosts.get(parsed.getHost())) {
                    targets.add(TargetUri.builder()
                            .uri(uri)
                            .resolvedAddress(address)
                            .build());
                }
            }
            return ImmutableServiceConfigurationWithTargets.of(input, targets.build());
        }
    };
}
