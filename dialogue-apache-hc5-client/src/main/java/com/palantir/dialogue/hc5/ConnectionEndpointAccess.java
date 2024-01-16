/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.io.ConnectionEndpoint;

final class ConnectionEndpointAccess {

    private static final SafeLogger log = SafeLoggerFactory.get(ConnectionEndpointAccess.class);

    private static final String INTERNAL_EXEC_RUNTIME_FQCN =
            "org.apache.hc.client5.http.impl.classic.InternalExecRuntime";

    @Nullable
    private static final Class<? extends ExecRuntime> INTERNAL_EXEC_RUNTIME_CLASS = findInternalExecRuntime();

    @Nullable
    private static final Method ENSURE_VALID_METHOD = findEnsureValid(INTERNAL_EXEC_RUNTIME_CLASS);

    @Nullable
    static ConnectionEndpoint getConnectionEndpoint(@Nullable ExecRuntime runtime) {
        if (ENSURE_VALID_METHOD != null
                && INTERNAL_EXEC_RUNTIME_CLASS != null
                && INTERNAL_EXEC_RUNTIME_CLASS.isInstance(runtime)) {
            try {
                return (ConnectionEndpoint) ENSURE_VALID_METHOD.invoke(runtime);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalStateException) {
                    log.debug("Connection not acquired or already released", e);
                } else {
                    log.warn("Failed to extract a ConnectionEndpoint from ExecRuntime", e);
                }
            } catch (Throwable t) {
                log.warn("Failed to extract a ConnectionEndpoint from ExecRuntime", t);
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Class<? extends ExecRuntime> findInternalExecRuntime() {
        try {
            return (Class<? extends ExecRuntime>)
                    Class.forName(INTERNAL_EXEC_RUNTIME_FQCN, false, ExecRuntime.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Nullable
    private static Method findEnsureValid(@Nullable Class<? extends ExecRuntime> internalExecRuntime) {
        if (internalExecRuntime != null) {
            try {
                Method method = internalExecRuntime.getDeclaredMethod("ensureValid");
                method.setAccessible(true);
                return method;
            } catch (Throwable t) {
                log.info("Failed to load the 'ensureValid' method on InternalExecRuntime", t);
            }
        }
        return null;
    }

    private ConnectionEndpointAccess() {}
}
