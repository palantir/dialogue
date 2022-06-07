/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import java.io.IOException;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.protocol.HttpContext;

enum HttpClientExecRuntimeAttributeInterceptor implements ExecChainHandler {
    INSTANCE;

    private static final String ATTRIBUTE = "dialogueExecRuntime";

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, Scope scope, ExecChain chain)
            throws IOException, HttpException {
        scope.clientContext.setAttribute(ATTRIBUTE, scope.execRuntime);
        return chain.proceed(request, scope);
    }

    @Nullable
    static ExecRuntime get(HttpContext context) {
        Object result = context.getAttribute(ATTRIBUTE);
        if (result instanceof ExecRuntime) {
            return (ExecRuntime) result;
        }
        return null;
    }
}
