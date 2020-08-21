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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Optional;

interface NodeSelectionStrategy {

    EndpointLimitedChannel createSelectorOverChannels(ImmutableList<EndpointLimitedChannel> singleUriChannels);

    static NodeSelectionStrategy createNodeSelectionStrategy(Config cf) {
        if (cf.clientConf().uris().size() == 0) {
            return new NodeSelectionStrategy() {
                @Override
                public EndpointLimitedChannel createSelectorOverChannels(
                        ImmutableList<EndpointLimitedChannel> _singleUriChannels) {
                    return new EndpointLimitedChannel() {
                        @Override
                        public Optional<ListenableFuture<Response>> maybeExecute(Request _request) {
                            return Optional.of(Futures.immediateFailedFuture(new SafeIllegalStateException(
                                    "There are no URIs configured to handle requests",
                                    SafeArg.of("channel", cf.channelName()))));
                        }
                    };
                }
            };
        }

        if (cf.clientConf().uris().size() == 1) {
            return new NodeSelectionStrategy() {
                @Override
                public EndpointLimitedChannel createSelectorOverChannels(
                        ImmutableList<EndpointLimitedChannel> singleUriChannels) {
                    return singleUriChannels.get(0);
                }
            };
        }

        switch (cf.clientConf().nodeSelectionStrategy()) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE: // TODO(dfox): implement this
                return PinUntilError.create(cf);
            case ROUND_ROBIN:
                return Balanced.create(cf);
            default:
                throw new SafeIllegalArgumentException("Unsupported NSS");
        }
    }
}
