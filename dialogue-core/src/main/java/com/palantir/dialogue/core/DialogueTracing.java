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
import com.palantir.tracing.TagTranslator.TagAdapter;
import com.palantir.tracing.api.TraceTags;
import java.util.Map;

/** Internal utility functionality to support tracing dialogue requests. */
final class DialogueTracing {

    enum EndpointTagTranslator implements TagTranslator<Endpoint> {
        INSTANCE;

        @Override
        public <T> void translate(TagAdapter<T> adapter, T target, Endpoint endpoint) {
            adapter.tag(target, "endpointService", endpoint.serviceName());
            adapter.tag(target, "endpointName", endpoint.endpointName());
            adapter.tag(target, TraceTags.HTTP_METHOD, endpoint.httpMethod().toString());
        }
    }

    static ImmutableMap<String, String> tracingTags(Endpoint endpoint) {
        // Might as well delegate to the EndpointTagTranslator to ensure all implementations
        // stay in sync.
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        EndpointTagTranslator.INSTANCE.translate(ImmutableMapTagAdapter.INSTANCE, builder, endpoint);
        return builder.build();
    }

    static ImmutableMap<String, String> tracingTags(Config cf) {
        return ImmutableMap.of(
                "channel", cf.channelName(), "mesh", Boolean.toString(cf.mesh() == MeshMode.USE_EXTERNAL_MESH));
    }

    static TagTranslator<Response> responseTranslator(ImmutableMap<String, String> tags) {
        return new TagTranslator<Response>() {

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
        return new TagTranslator<Throwable>() {

            @Override
            public <T> void translate(TagAdapter<T> sink, T target, Throwable response) {
                sink.tag(target, tags);
                sink.tag(target, "outcome", "failure");
                sink.tag(target, "cause", response.getClass().getSimpleName());
            }
        };
    }

    private DialogueTracing() {}

    enum ImmutableMapTagAdapter implements TagAdapter<ImmutableMap.Builder<String, String>> {
        INSTANCE;

        @Override
        public void tag(ImmutableMap.Builder<String, String> target, String key, String value) {
            target.put(key, value);
        }

        @Override
        public void tag(ImmutableMap.Builder<String, String> target, Map<String, String> tags) {
            target.putAll(tags);
        }
    }
}
