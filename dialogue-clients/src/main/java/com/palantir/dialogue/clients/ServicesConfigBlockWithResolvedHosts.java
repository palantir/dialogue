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
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.clients.DialogueDnsDiscoveryMetrics.Resolution_Result;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.logsafe.DoNotLog;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.ProxySelector;
import java.util.Objects;

@DoNotLog
final class ServicesConfigBlockWithResolvedHosts {
    private static final SafeLogger log = SafeLoggerFactory.get(ServicesConfigBlockWithResolvedHosts.class);

    private final ServicesConfigBlock scb;
    private final ImmutableSetMultimap<String, TargetUri> resolvedAddresses;

    ServicesConfigBlockWithResolvedHosts(
            ServicesConfigBlock scb, ImmutableSetMultimap<String, TargetUri> resolvedAddresses) {
        this.scb = scb;
        this.resolvedAddresses = resolvedAddresses;
    }

    ServicesConfigBlock scb() {
        return scb;
    }

    ImmutableSetMultimap<String, TargetUri> targets() {
        return resolvedAddresses;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ServicesConfigBlockWithResolvedHosts that = (ServicesConfigBlockWithResolvedHosts) other;
        if (!Objects.equals(scb, that.scb)) {
            return false;
        }
        return Objects.equals(resolvedAddresses, that.resolvedAddresses);
    }

    @Override
    public int hashCode() {
        int result = scb != null ? scb.hashCode() : 0;
        result = 31 * result + (resolvedAddresses != null ? resolvedAddresses.hashCode() : 0);
        return result;
    }

    @Override
    @DoNotLog
    public String toString() {
        return "ServicesConfigBlockWithResolvedHosts{scb=" + scb + ", resolvedAddresses=" + resolvedAddresses + '}';
    }

    static Refreshable<ServicesConfigBlockWithResolvedHosts> from(Refreshable<ServicesConfigBlock> input) {
        // TODO(ckozak): refresh DNS info
        //   e.g. wiring DialogueDnsResolution.refreshableUris(serviceName, registry, config.uris(), proxySelector)
        //   currently this only handles DNS resolution once when the configuration is updated.
        return input.map(block -> block == null ? null : new ServicesConfigBlockWithResolvedHosts(block, from(block)));
    }

    @SuppressWarnings("deprecation") // singleton registry
    private static ImmutableSetMultimap<String, TargetUri> from(ServicesConfigBlock block) {
        ImmutableSetMultimap.Builder<String, TargetUri> builder = ImmutableSetMultimap.builder();
        ServiceConfigurationFactory factory = ServiceConfigurationFactory.of(block);
        TaggedMetricRegistry registry = SharedTaggedMetricRegistries.getSingleton();
        DialogueDnsDiscoveryMetrics metrics = DialogueDnsDiscoveryMetrics.of(registry);
        for (String serviceName : block.services().keySet()) {
            String channelName = ChannelNames.reloading(serviceName);
            // Build each config one at a time, otherwise a malformed service will prevent all targets
            // from being resolved.
            try {
                ServiceConfiguration config = factory.get(serviceName);
                ProxySelector proxySelector = config.proxy()
                        .map(ClientConfigurations::createProxySelector)
                        .orElseGet(ProxySelector::getDefault);
                ImmutableList<TargetUri> targets = DialogueDnsResolution.resolveUris(
                        serviceName,
                        config.uris(),
                        proxySelector,
                        metrics.resolution()
                                .channelName(channelName)
                                .result(Resolution_Result.SUCCESS)
                                .build(),
                        metrics.resolution()
                                .channelName(channelName)
                                .result(Resolution_Result.FAILURE)
                                .build());
                targets.forEach(target -> builder.put(target.uri(), target));
            } catch (Throwable t) {
                log.warn(
                        "Failed to construct ServiceConfiguration for service {}",
                        SafeArg.of("service", serviceName),
                        t);
            }
        }
        return builder.build();
    }
}
