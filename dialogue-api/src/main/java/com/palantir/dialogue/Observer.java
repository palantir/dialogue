/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
 * A callback interface for RPC responses. Successful calls are presented to {@link #success}. We distinguish between
 * two types of non-successful responses:
 * <p>
 * <ul>
 * <li>Application-level errors from the server, e.g., authorization problems, invalid arguments, etc., are presented
 * to {@link #failure} as a {@link RemoteException} explaining the type of failure. These are typically "expected"
 * errors in the sense that they are part of the API contract between client and server.</li>
 * <li>All other errors, e.g., failed connections or DNS lookups, failed deserialization of server responses, etc., are
 * presented to {@link #exception}. These are typically "unexpected" errors in the sense that they represent a
 * configuration problem or a bug.</li>
 * </ul>
 */
public interface Observer<RespT> {
    void success(RespT value);
    void failure(RemoteException error);
    void exception(Throwable throwable);
}
