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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Optional;
import javax.annotation.Nullable;

interface Statistics extends LimitedChannel {

    /** Returns an interface that allow us to record info at the beginning and end of a request. */
    // TODO(dfox): this doesn't allow tracking info about LimitedChannels, only a Channel
    InFlightStage recordStart(Channel channel, Endpoint endpoint, Request request);

    interface InFlightStage {
        void recordComplete(@Nullable Response response, @Nullable Throwable throwable);
        // TODO(dfox): allow recording more detailed statistics about the body upload / download time?
    }

    /** Return the index of the best channel. */
    Optional<Channel> getBest(Endpoint endpoint);

    @Override
    default Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        Optional<Channel> best = getBest(endpoint);
        if (!best.isPresent()) {
            return Optional.empty();
        }

        Channel channel = best.get();

        Statistics.InFlightStage inFlightStage = recordStart(channel, endpoint, request);
        ListenableFuture<Response> response = channel.execute(endpoint, request);
        Futures.addCallback(
                response,
                new FutureCallback<Response>() {
                    @Override
                    public void onSuccess(Response result) {
                        inFlightStage.recordComplete(result, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        inFlightStage.recordComplete(null, throwable);
                    }
                },
                MoreExecutors.directExecutor());

        return Optional.of(response);
    }
}
