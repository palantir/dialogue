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

import com.palantir.tracing.CloseableTracer;
import java.io.IOException;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;

/** {@link ExecChainHandler} which adds a tracing span. */
enum TracingExecChainHandler implements ExecChainHandler {
    INSTANCE;

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, Scope scope, ExecChain chain)
            throws IOException, HttpException {
        try (CloseableTracer ignored = CloseableTracer.startSpan("Dialogue ExecChain.execute")) {
            return chain.proceed(request, scope);
        }
    }
}
