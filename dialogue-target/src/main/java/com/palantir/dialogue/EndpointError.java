/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import com.palantir.logsafe.Safe;

public abstract class EndpointError<T> {
    @Safe
    private final String errorCode;

    @Safe
    private final String errorName;

    @Safe
    private final String errorInstanceId;

    private final T params;

    protected EndpointError(String errorCode, String errorName, String errorInstanceId, T params) {
        this.errorCode = errorCode;
        this.errorName = errorName;
        this.errorInstanceId = errorInstanceId;
        this.params = params;
    }

    public final String getErrorCode() {
        return errorCode;
    }

    public final String getErrorName() {
        return errorName;
    }

    public final String getErrorInstanceId() {
        return errorInstanceId;
    }

    public final T getParams() {
        return params;
    }
}
