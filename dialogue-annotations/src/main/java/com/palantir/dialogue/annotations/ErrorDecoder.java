/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations;

import com.palantir.dialogue.Response;

public interface ErrorDecoder {
    boolean isError(Response response);

    RuntimeException decode(Response response);

    final class None implements ErrorDecoder {

        @Override
        public boolean isError(Response _response) {
            return false;
        }

        @Override
        public RuntimeException decode(Response _response) {
            throw new UnsupportedOperationException("This should not happen");
        }
    }
}
