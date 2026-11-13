/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingKey;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Best-effort rendezvous hashing for requests carrying a locally generated routing key. */
final class ConsistentHashingNodeSelectionStrategyChannel implements LimitedChannel {
    private static final RequestAttachmentKey<AttemptedTargets> ATTEMPTED_TARGETS =
            RequestAttachmentKey.create(AttemptedTargets.class);

    private final List<Node> nodes;
    private final LimitedChannel fallback;

    ConsistentHashingNodeSelectionStrategyChannel(
            List<TargetUri> targets, List<LimitedChannel> channels, LimitedChannel fallback) {
        Preconditions.checkArgument(targets.size() == channels.size(), "Targets and channels must match");
        this.fallback = fallback;
        List<Node> result = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            TargetUri target = targets.get(i);
            byte[] uri = target.uri().getBytes(StandardCharsets.UTF_8);
            byte[] address =
                    target.resolvedAddress().map(value -> value.getAddress()).orElseGet(() -> new byte[0]);
            // Include the resolved address: one discovery URI may represent several physical nodes.
            long identity = Hashing.murmur3_128()
                    .newHasher()
                    .putInt(uri.length)
                    .putBytes(uri)
                    .putInt(address.length)
                    .putBytes(address)
                    .hash()
                    .asLong();
            result.add(new Node(target, channels.get(i), identity));
        }
        this.nodes = List.copyOf(result);
    }

    /** Give each logical call its own retry state, including when a Request is executed more than once. */
    static Request prepareRequest(Request request) {
        if (request.attachments().getOrDefault(RoutingKey.ATTACHMENT_KEY, null) == null) {
            return request;
        }
        return Request.builder()
                .from(request)
                .putAttachment(ATTEMPTED_TARGETS, new AttemptedTargets())
                .build();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        RoutingKey key = request.attachments().getOrDefault(RoutingKey.ATTACHMENT_KEY, null);
        if (key == null || nodes.size() < 2) {
            return fallback.maybeExecute(endpoint, request, limitEnforcement);
        }
        // Explicit sticky sessions take precedence over best-effort affinity.
        return StickyAttachments.maybeExecuteOnSticky(
                (ep, req, limits) -> executeHashed(key, ep, req, limits), endpoint, request, limitEnforcement);
    }

    private Optional<ListenableFuture<Response>> executeHashed(
            RoutingKey key, Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        List<ScoredNode> ranked = nodes.stream()
                .map(node -> new ScoredNode(
                        node,
                        Hashing.murmur3_128()
                                .newHasher()
                                .putLong(key.hash())
                                .putLong(node.identity())
                                .hash()
                                .asLong()))
                .sorted(Comparator.<ScoredNode, Long>comparing(ScoredNode::score, Long::compareUnsigned)
                        .reversed()
                        .thenComparing(scored -> scored.node().target()))
                .toList();
        AttemptedTargets attempted = request.attachments().getOrDefault(ATTEMPTED_TARGETS, null);
        if (attempted != null && nodes.stream().allMatch(node -> attempted.failed.contains(node.target()))) {
            attempted.failed.clear();
        }
        for (ScoredNode scored : ranked) {
            Node node = scored.node();
            if (attempted != null && attempted.failed.contains(node.target())) {
                continue;
            }
            Optional<ListenableFuture<Response>> response =
                    StickyAttachments.maybeAddStickyToken(node.channel(), endpoint, request, limitEnforcement);
            if (response.isPresent()) {
                if (attempted == null) {
                    return response;
                }
                // Mark the failed node before RetryingChannel sees completion. This layer never initiates a retry.
                return Optional.of(DialogueFutures.addDirectCallback(response.get(), new FutureCallback<>() {
                    @Override
                    public void onSuccess(@Nullable Response result) {
                        if (Responses.isInternalServerError(result)
                                || (result != null
                                        && Responses.isRetryableQos(result)
                                        && !Responses.isQosDueToCustom(result)
                                        && !Responses.isTooManyRequests(result))) {
                            attempted.failed.add(node.target());
                        }
                    }

                    @Override
                    public void onFailure(Throwable _throwable) {
                        attempted.failed.add(node.target());
                    }
                }));
            }
        }
        // All eligible targets are limited. The existing queue will wait and try again.
        return Optional.empty();
    }

    private record Node(TargetUri target, LimitedChannel channel, long identity) {}

    private record ScoredNode(Node node, long score) {}

    private static final class AttemptedTargets {
        private final Set<TargetUri> failed = ConcurrentHashMap.newKeySet();
    }
}
