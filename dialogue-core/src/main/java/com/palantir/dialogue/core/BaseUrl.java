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

package com.palantir.dialogue.core;

import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import java.net.URL;

/** Convenience utility around {@link UrlBuilder}. */
public final class BaseUrl {

    private final UrlBuilder builder;

    public static BaseUrl of(URL baseUrl) {
        return new BaseUrl(UrlBuilder.from(baseUrl));
    }

    private BaseUrl(UrlBuilder builder) {
        this.builder = builder;
    }

    public URL render(Endpoint endpoint, Request request) {
        UrlBuilder url = builder.newBuilder();
        endpoint.renderPath(request.pathParams(), url);
        request.queryParams().forEach(url::queryParam);
        return url.build();
    }

    @Override
    public String toString() {
        return "BaseUrl{builder=" + builder + '}';
    }
}
