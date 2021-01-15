/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.UrlBuilder;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Generated;

@Generated("com.palantir.conjure.java.services.dialogue.DialogueEndpointsGenerator")
enum DialogueSampleEndpoints implements Endpoint {
    voidToVoid {
        private final PathTemplate pathTemplate =
                PathTemplate.builder().fixed("voidToVoid").build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "SampleService";
        }

        @Override
        public String endpointName() {
            return "voidToVoid";
        }

        @Override
        public String version() {
            return VERSION;
        }
    },

    objectToObject {
        private final PathTemplate pathTemplate = PathTemplate.builder()
                .fixed("objectToObject")
                .fixed("objects")
                .variable("path")
                .build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
        }

        @Override
        public String serviceName() {
            return "SampleService";
        }

        @Override
        public String endpointName() {
            return "objectToObject";
        }

        @Override
        public String version() {
            return VERSION;
        }
    },

    getOptionalBinary {
        private final PathTemplate pathTemplate =
                PathTemplate.builder().fixed("getOptionalBinary").build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "SampleService";
        }

        @Override
        public String endpointName() {
            return "getOptionalBinary";
        }

        @Override
        public String version() {
            return VERSION;
        }
    },

    getMyAlias {
        private final PathTemplate pathTemplate =
                PathTemplate.builder().fixed("getMyAlias").build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "SampleService";
        }

        @Override
        public String endpointName() {
            return "getMyAlias";
        }

        @Override
        public String version() {
            return VERSION;
        }
    },

    getMyAlias2 {
        private final PathTemplate pathTemplate =
                PathTemplate.builder().fixed("getMyAlias2").build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "SampleService";
        }

        @Override
        public String endpointName() {
            return "getMyAlias2";
        }

        @Override
        public String version() {
            return VERSION;
        }
    };

    private static final String VERSION = Optional.ofNullable(
                    DialogueSampleEndpoints.class.getPackage().getImplementationVersion())
            .orElse("0.0.0");
}
