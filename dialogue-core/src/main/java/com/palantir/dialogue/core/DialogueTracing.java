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

import com.google.common.collect.ImmutableMap;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Response;
import com.palantir.tracing.TagTranslator;
import com.palantir.tracing.api.TraceTags;

/** Internal utility functionality to support tracing dialogue requests. */
final class DialogueTracing {

    static ImmutableMap<String, String> tracingTags(Endpoint endpoint) {
        return ImmutableMap.of(
                "endpointService",
                endpoint.serviceName(),
                "endpointName",
                endpoint.endpointName(),
                TraceTags.HTTP_METHOD,
                endpoint.httpMethod().toString());
    }

    static ImmutableMap<String, String> tracingTags(Config cf) {
        return ImmutableMap.of(
                "channel", cf.channelName(),
                "mesh", Boolean.toString(cf.mesh() == MeshMode.USE_EXTERNAL_MESH));
    }

    static ImmutableMap<String, String> tracingTags(Config cf, int hostIndex) {
        return ImmutableMap.of(
                "channel",
                cf.channelName(),
                "mesh",
                Boolean.toString(cf.mesh() == MeshMode.USE_EXTERNAL_MESH),
                "hostIndex",
                Integer.toString(hostIndex));
    }

    static TagTranslator<Response> responseTranslator(ImmutableMap<String, String> tags) {
        return new TagTranslator<>() {
            @Override
            public <T> void translate(TagAdapter<T> sink, T target, Response response) {
                sink.tag(target, tags);
                int status = response.code();
                sink.tag(target, "outcome", status / 100 == 2 ? "success" : "failure");
                sink.tag(target, TraceTags.HTTP_STATUS_CODE, Integer.toString(status));
            }
        };
    }

    static TagTranslator<Throwable> failureTranslator(ImmutableMap<String, String> tags) {
        return new TagTranslator<>() {
            @Override
            public <T> void translate(TagAdapter<T> sink, T target, Throwable response) {
                sink.tag(target, tags);
                sink.tag(target, "outcome", "failure");
                sink.tag(target, "cause", response.getClass().getSimpleName());
            }
        };
    }

    private DialogueTracing() {}
}
