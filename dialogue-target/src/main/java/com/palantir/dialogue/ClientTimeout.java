/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
 * Marks an exception that means a client stopped waiting for a call to another service, either because no response
 * arrived within the client's timeout or because the request waited too long to be sent.
 * <p>
 * Only {@link Throwable} subclasses should implement this interface. A server that fails a request because of a
 * {@code ClientTimeout} can report it to its own caller as an expired internal deadline
 * ({@code DeadlineExpiredException.internal()}) instead of as an internal error.
 * <p>
 * Check for it with {@code instanceof}: Java doesn't allow an interface type in a {@code catch} clause.
 */
public interface ClientTimeout {}
