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

package com.palantir.dialogue;

import java.util.Map;

public enum TestEndpoint implements Endpoint {
    GET {
        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }
    },
    POST {
        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
        }
    },
    PUT {
        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.PUT;
        }
    },
    DELETE {
        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.DELETE;
        }
    };

    @Override
    public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

    @Override
    public String serviceName() {
        return "service";
    }

    @Override
    public String endpointName() {
        return "endpoint";
    }

    @Override
    public String version() {
        return "1.0.0";
    }
}
