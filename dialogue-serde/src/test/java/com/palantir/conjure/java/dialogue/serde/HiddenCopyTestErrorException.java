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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableErrorProvider;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorParameters;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorSerializableError;

/**
 * An exception for the test error that {@link ConjureBodySerDeErrorsTest} copies into a hidden class. It's a top-level
 * class because a hidden copy of a nested class disagrees with its enclosing class about their relationship.
 */
public final class HiddenCopyTestErrorException extends RemoteException
        implements SerializableErrorProvider<TestErrorParameters> {
    private final TestErrorSerializableError error;

    // Constructor needs to be public so that the exception can be created via reflection
    public HiddenCopyTestErrorException(TestErrorSerializableError error, int status) {
        super(error.toSerializableError(), status);
        this.error = error;
    }

    @Override
    public TestErrorSerializableError error() {
        return error;
    }
}
