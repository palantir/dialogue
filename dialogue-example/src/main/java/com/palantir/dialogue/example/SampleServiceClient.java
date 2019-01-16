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

package com.palantir.dialogue.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Call;
import com.palantir.dialogue.Calls;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Deserializers;
import com.palantir.dialogue.DialogueOkHttpErrorDecoder;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Exceptions;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.OkHttpErrorDecoder;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.Serializers;
import com.palantir.logsafe.Preconditions;
import java.util.Map;

// Example of the implementation code conjure would generate for a simple SampleService.
public final class SampleServiceClient {

    private SampleServiceClient() {}

    private static final Endpoint<String, String> STRING_TO_STRING = new Endpoint<String, String>() {
        private final PathTemplate pathTemplate = PathTemplate.of(ImmutableList.of(
                PathTemplate.Segment.fixed("stringToString"),
                PathTemplate.Segment.fixed("objects"),
                PathTemplate.Segment.variable("objectId")));

        @Override
        public String renderPath(Map<String, String> params) {
            return pathTemplate.fill(params);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
        }

        @Override
        public Serializer<String> requestSerializer() {
            // TODO(rfink): Inject "SerializerFactory" or similar, avoid concrete dependency on Jackson or JSON
            return Serializers.jackson("stringToString", new ObjectMapper());
        }

        @Override
        public Deserializer<String> responseDeserializer() {
            return Deserializers.jackson("stringToString", new ObjectMapper(), new TypeReference<String>() {});
        }

        @Override
        public OkHttpErrorDecoder errorDecoder() {
            return DialogueOkHttpErrorDecoder.INSTANCE;
        }
    };

    private static final Endpoint<Void, Void> VOID_TO_VOID = new Endpoint<Void, Void>() {
        private final PathTemplate pathTemplate = PathTemplate.of(ImmutableList.of(
                PathTemplate.Segment.fixed("voidToVoid")));

        @Override
        public String renderPath(Map<String, String> params) {
            return pathTemplate.fill(params);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public Serializer<Void> requestSerializer() {
            return Serializers.failing();
        }

        @Override
        public Deserializer<Void> responseDeserializer() {
            return Deserializers.empty("voidToVoid");
        }

        @Override
        public OkHttpErrorDecoder errorDecoder() {
            return DialogueOkHttpErrorDecoder.INSTANCE;
        }
    };

    /** Returns a new blocking {@link SampleService} implementation whose calls are executed on the given channel. */
    public static SampleService blocking(Channel channel) {
        return new SampleService() {

            @Override
            public String stringToString(String objectId, String header, String body) {
                Preconditions.checkNotNull(objectId, "objectId parameter must not be null");
                Preconditions.checkNotNull(header, "header parameter must not be null");
                Preconditions.checkNotNull(body, "body parameter must not be null");
                Request<String> request = Request.<String>builder()
                        .putPathParams("objectId", objectId)
                        .putHeaderParams("headerKey", header)
                        .body(body)
                        .build();

                Call<String> call = channel.createCall(STRING_TO_STRING, request);
                ListenableFuture<String> response = Calls.toFuture(call);
                try {
                    return response.get();
                } catch (Throwable t) {
                    throw Exceptions.unwrapExecutionException(t);
                }
            }

            @Override
            public void voidToVoid() {
                Request<Void> request = Request.<Void>builder().build();
                Call<Void> call = channel.createCall(VOID_TO_VOID, request);
                ListenableFuture<Void> response = Calls.toFuture(call);
                try {
                    response.get();
                } catch (Throwable t) {
                    throw Exceptions.unwrapExecutionException(t);
                }
            }
        };
    }

    /**
     * Returns a new asynchronous {@link AsyncSampleService} implementation whose calls are executed on the given
     * channel.
     */
    public static AsyncSampleService async(Channel channel) {
        return new AsyncSampleService() {
            @Override
            public Call<String> stringToString(String objectId, String header, String body) {
                Preconditions.checkNotNull(objectId, "objectId parameter must not be null");
                Preconditions.checkNotNull(header, "header parameter must not be null");
                Preconditions.checkNotNull(body, "body parameter must not be null");
                Request<String> request = Request.<String>builder()
                        .putPathParams("objectId", objectId)
                        .putHeaderParams("headerKey", header)
                        .body(body)
                        .build();

                Call<String> call = channel.createCall(STRING_TO_STRING, request);
                return call;
            }

            @Override
            public Call<Void> voidToVoid() {
                Request<Void> request = Request.<Void>builder().build();

                Call<Void> call = channel.createCall(VOID_TO_VOID, request);
                return call;
            }
        };
    }
}
