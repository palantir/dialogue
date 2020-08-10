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

package com.palantir.dialogue.hc5;

import com.google.common.util.concurrent.Runnables;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reflective shim to allow consumers on new runtime versions to take advantage of java.lang.ref.Cleaner. */
final class CleanerSupport {
    private static final Logger log = LoggerFactory.getLogger(CleanerSupport.class);

    @Nullable
    private static final BiFunction<Object, Runnable, Runnable> cleaner = createCleaner();

    /**
     * Arguments are passed to {@code java.lang.ref.Cleaner.register(Object, Runnable)} on supported runtimes.
     * This method does nothing on java 8.
     */
    static Runnable register(Object object, Runnable action) {
        if (cleaner != null) {
            return cleaner.apply(object, action);
        }
        return Runnables.doNothing();
    }

    static boolean enabled() {
        return cleaner != null;
    }

    private CleanerSupport() {}

    @Nullable
    private static BiFunction<Object, Runnable, Runnable> createCleaner() {
        try {
            Class<?> cleanerType = Class.forName("java.lang.ref.Cleaner");
            Class<?> cleanableType = Class.forName("java.lang.ref.Cleaner$Cleanable");
            Method cleanerCreate = cleanerType.getMethod("create");
            Method cleanerRegister = cleanerType.getMethod("register", Object.class, Runnable.class);
            Method cleanableClean = cleanableType.getMethod("clean");
            Object cleanerObject = cleanerCreate.invoke(null);
            return (object, action) -> {
                try {
                    Object cleanable = cleanerRegister.invoke(cleanerObject, object, action);
                    return cleanableToRunnable(cleanableClean, cleanable);
                } catch (ReflectiveOperationException e) {
                    throw handle(e);
                }
            };
        } catch (ReflectiveOperationException e) {
            log.debug("Failed to locate java.lang.ref.Cleaner", e);
            return null;
        }
    }

    private static Runnable cleanableToRunnable(Method cleanableClean, Object cleanable) {
        return () -> {
            try {
                cleanableClean.invoke(cleanable);
            } catch (ReflectiveOperationException e) {
                throw handle(e);
            }
        };
    }

    private static RuntimeException handle(ReflectiveOperationException roe) {
        if (roe instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) roe;
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
        }
        throw new SafeRuntimeException("Unexpected reflective invocation failure", roe);
    }
}
