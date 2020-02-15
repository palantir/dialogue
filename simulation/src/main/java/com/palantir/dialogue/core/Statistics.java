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

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import javax.annotation.Nullable;

interface Statistics extends LimitedChannel {

    /** Returns an interface that allow us to record info at the beginning and end of a request. */
    // TODO(dfox): this doesn't allow tracking info about LimitedChannels, only a Channel
    InFlightStage recordStart(Channel channel, Endpoint endpoint, Request request);

    interface InFlightStage {
        void recordComplete(@Nullable Response response, @Nullable Throwable throwable);
        // TODO(dfox): allow recording more detailed statistics about the body upload / download time?
    }
}
