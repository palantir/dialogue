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

import java.util.concurrent.ExecutionException;

/** Provides utility functions for exception handling. */
public final class Exceptions {

    private Exceptions() {}

    /**
     * If the given {@link Throwable} is an {@link ExecutionException} with a non-null {@link Throwable#cause cause},
     * returns the cause, possibly wrapped in a {@link RuntimeException} unless it is already a RuntimeException. Else,
     * returns the given throwable itself if it is a {@link RuntimeException}, or else the given throwable wrapped in a
     * {@link RuntimeException}.
     */
    public static RuntimeException unwrapExecutionException(Throwable throwable) {
        if (throwable instanceof ExecutionException) {
            if (throwable.getCause() != null) {
                Throwable cause = throwable.getCause();
                throw throwable.getCause() instanceof RuntimeException
                        ? (RuntimeException) cause
                        : new RuntimeException(cause);
            }
        }
        return throwable instanceof RuntimeException
                ? (RuntimeException) throwable
                : new RuntimeException(throwable);
    }
}
