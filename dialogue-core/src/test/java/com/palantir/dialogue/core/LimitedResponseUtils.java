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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Response;
import java.util.Optional;

public class LimitedResponseUtils {
    private static final LimitedResponse.Cases<Boolean> isSuccess =
            LimitedResponses.cases(_response -> false, () -> false, _response -> false, _response -> true);

    static Optional<Response> getSuccess(ListenableFuture<LimitedResponse> result) {
        LimitedResponse response = Futures.getUnchecked(result);
        if (response.matches(isSuccess)) {
            return LimitedResponses.getResponse(response);
        }
        return Optional.empty();
    }

    static void assertThatIsClientLimited(ListenableFuture<LimitedResponse> result) {
        assertThat(Futures.getUnchecked(result).matches(LimitedResponse.isClientLimited))
                .isTrue();
    }

    static void assertThatIsSuccess(ListenableFuture<LimitedResponse> result) {
        assertThat(Futures.getUnchecked(result).matches(isSuccess)).isTrue();
    }

    private LimitedResponseUtils() {}
}
