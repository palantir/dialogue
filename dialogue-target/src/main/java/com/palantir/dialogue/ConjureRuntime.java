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

/**
 * {@link ConjureRuntime} is the anchor for all non-generated logic used by generated clients.
 * <p>
 * The {@link ConjureRuntime} and provided interfaces {@link BodySerDe}, {@link PlainSerDe}, and
 * are internal API, no guarantees are made for custom implementations.
 */
public interface ConjureRuntime {

    /** Provides the {@link BodySerDe} used to deserialize and serialize request and response bodies respectively. */
    BodySerDe bodySerDe();

    /** Provides the {@link PlainSerDe} used to parse request path, query, and header parameters. */
    PlainSerDe plainSerDe();

    /** Provides the {@link Clients} used to facilitate making requests. */
    Clients clients();

    /** Provides the {@link Clients} used to facilitate making requests. */
    ConjureRuntime toBlocking();
}
