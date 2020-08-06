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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reflective shim to allow consumers on new runtime versions to take advantage of java.lang.ref.Cleaner. */
final class CleanerSupport {
    private static final Logger log = LoggerFactory.getLogger(CleanerSupport.class);

    @Nullable
    private static final BiConsumer<Object, Runnable> cleaner = createCleaner();

    /**
     * Arguments are passed to {@code java.lang.ref.Cleaner.register(Object, Runnable)} on supported runtimes.
     * This method does nothing on java 8.
     */
    static void register(Object object, Runnable action) {
        if (cleaner != null) {
            cleaner.accept(object, action);
        }
    }

    static boolean enabled() {
        return cleaner != null;
    }

    private CleanerSupport() {}

    @Nullable
    private static BiConsumer<Object, Runnable> createCleaner() {
        try {
            Class<?> cleanerType = Class.forName("java.lang.ref.Cleaner");
            Method cleanerCreate = cleanerType.getMethod("create");
            Method cleanerRegister = cleanerType.getMethod("register", Object.class, Runnable.class);
            Object cleanerObject = cleanerCreate.invoke(null);
            return (object, action) -> {
                try {
                    cleanerRegister.invoke(cleanerObject, object, action);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new SafeRuntimeException("Failed to register tasks with the cleaner", e);
                } catch (IllegalAccessException e) {
                    throw new SafeRuntimeException("Failed to register tasks with the cleaner", e);
                }
            };
        } catch (ReflectiveOperationException e) {
            log.debug("Failed to locate java.lang.ref.Cleaner", e);
            return null;
        }
    }
}
