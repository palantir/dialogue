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

package com.palantir.dialogue;

import com.palantir.conjure.java.api.errors.RemoteException;

/**
 * Extracts and returns a {@link RemoteException} from an {@link Response}.
 * The extracted {@link RemoteException} is returned rather than thrown. Decoders may throw exceptions (other than
 * {@link RemoteException}) if a {@link RemoteException} could not be extracted, e.g., when the given {@link
 * Response} does not adhere to an expected format.
 */
public interface ErrorDecoder {

    boolean isError(Response response);

    RuntimeException decode(Response response);
}
