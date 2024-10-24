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

package com.palantir.dialogue.core;

import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReasons;
import com.palantir.conjure.java.api.errors.QosReasons.QosResponseDecodingAdapter;
import com.palantir.dialogue.Response;
import java.util.Optional;

enum DialogueQosReasonDecoder implements QosResponseDecodingAdapter<Response> {
    INSTANCE;

    @Override
    public Optional<String> getFirstHeader(Response response, String headerName) {
        return response.getFirstHeader(headerName);
    }

    static QosReason parse(Response response) {
        return QosReasons.parseFromResponse(response, DialogueQosReasonDecoder.INSTANCE);
    }
}