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

import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TagKey;
import java.io.InputStream;
import java.util.Optional;

public final class StickySessioning {
    private static final TagKey<LimitedChannel> EXECUTED_ON = new TagKey<>(LimitedChannel.class);

    private StickySessioning() {}

    static LimitedChannel wrapWithRecordedChoiceSelection(LimitedChannel delegate) {
        return new RecordedChoiceChannel(delegate);
    }

    static ListenableFuture<Response> addExecutedOnTag(
            ListenableFuture<Response> responseFuture,
            LimitedChannel channel) {
        return Futures.transform(responseFuture,
                response -> new Response() {
                    @Override
                    public InputStream body() {
                        return response.body();
                    }

                    @Override
                    public int code() {
                        return response.code();
                    }

                    @Override
                    public ListMultimap<String, String> headers() {
                        return response.headers();
                    }

                    @Override
                    public void close() {
                        response.close();
                    }

                    @Override
                    public <T> Optional<T> getTag(TagKey<T> tagKey) {
                        if (tagKey == EXECUTED_ON) {
                            T ret = (T) channel;
                            return Optional.of(ret);
                        }
                        return response.getTag(tagKey);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static final class RecordedChoiceChannel implements LimitedChannel {
        private final LimitedChannel delegate;

        private RecordedChoiceChannel(LimitedChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint endpoint, Request request) {
            return request.getTag(EXECUTED_ON).orElse(delegate).maybeExecute(endpoint, request);
        }
    }

    private static final class StickySessionChannel implements Channel {
        private final Channel delegate;

        private FluentFuture<LimitedChannel> stuckChannel;

        private StickySessionChannel(Channel delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            if (stuckChannel != null) {
                return executeOnStuckChannel(endpoint, request);
            }
            ListenableFuture<Response> response = delegate.execute(endpoint, request);
            stuckChannel = FluentFuture.from(response)
                    .transform(r -> r.getTag(EXECUTED_ON)
                                    .orElseThrow(() -> new IllegalStateException("No channel used was tagged")),
                            MoreExecutors.directExecutor());
            return response;
        }

        private ListenableFuture<Response> executeOnStuckChannel(Endpoint endpoint, Request request) {
            return stuckChannel.transformAsync(channel -> delegate.execute(endpoint, Request.builder()
                    .from(request)
                    .putTags(EXECUTED_ON, channel)
                    .build()), MoreExecutors.directExecutor());
        }
    }
}
