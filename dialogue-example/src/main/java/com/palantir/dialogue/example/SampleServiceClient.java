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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Call;
import com.palantir.dialogue.Calls;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Exceptions;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.util.Map;

// Example of the implementation code conjure would generate for a simple SampleService.
public final class SampleServiceClient {

    private SampleServiceClient() {}

    private static final Endpoint STRING_TO_STRING = new Endpoint() {
        private final PathTemplate pathTemplate = PathTemplate.builder()
                .fixed("stringToString")
                .fixed("objects")
                .variable("objectId")
                .build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
        }
    };

    private static final Endpoint VOID_TO_VOID = new Endpoint() {
        private final PathTemplate pathTemplate = PathTemplate.builder()
                .fixed("voidToVoid")
                .build();

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            pathTemplate.fill(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }
    };

    /** Returns a new blocking {@link SampleService} implementation whose calls are executed on the given channel. */
    public static SampleService blocking(Channel channel, ConjureRuntime runtime) {
        return new SampleService() {

            private Serializer<String> stringToStringSerializer =
                    runtime.bodySerDe().serializer(new TypeMarker<String>() {});
            private Deserializer<String> stringToStringDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<String>() {});
            private Deserializer<Void> voidToVoidDeserializer = runtime.bodySerDe().emptyBodyDeserializer();

            @Override
            public String stringToString(String objectId, String header, String body) {
                Preconditions.checkNotNull(objectId, "objectId parameter must not be null");
                Preconditions.checkNotNull(header, "header parameter must not be null");
                Preconditions.checkNotNull(body, "body parameter must not be null");
                Request request = Request.builder()
                        .putPathParams("objectId", objectId)
                        .putHeaderParams("headerKey", header)
                        .body(stringToStringSerializer.serialize(body))
                        .build();

                Call call = channel.createCall(STRING_TO_STRING, request);
                ListenableFuture<Response> response = Calls.toFuture(call);
                try {
                    // TODO(rfink): Figure out how to inject read/write timeouts
                    return stringToStringDeserializer.deserialize(response.get());
                } catch (Throwable t) {
                    throw Exceptions.unwrapExecutionException(t);
                }
            }

            @Override
            public void voidToVoid() {
                Request request = Request.builder().build();

                Call call = channel.createCall(VOID_TO_VOID, request);
                ListenableFuture<Response> response = Calls.toFuture(call);
                try {
                    voidToVoidDeserializer.deserialize(response.get());
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
    public static AsyncSampleService async(Channel channel, ConjureRuntime runtime) {
        return new AsyncSampleService() {

            private Serializer<String> stringToStringSerializer =
                    runtime.bodySerDe().serializer(new TypeMarker<String>() {});
            private Deserializer<String> stringToStringDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<String>() {});
            private Deserializer<Void> voidToVoidDeserializer = runtime.bodySerDe().emptyBodyDeserializer();

            @Override
            public ListenableFuture<String> stringToString(String objectId, String header, String body) {
                Preconditions.checkNotNull(objectId, "objectId parameter must not be null");
                Preconditions.checkNotNull(header, "header parameter must not be null");
                Preconditions.checkNotNull(body, "body parameter must not be null");
                Request request = Request.builder()
                        .putPathParams("objectId", objectId)
                        .putHeaderParams("headerKey", header)
                        .body(stringToStringSerializer.serialize(body))
                        .build();

                Call call = channel.createCall(STRING_TO_STRING, request);
                return Futures.transform(
                        Calls.toFuture(call),
                        response -> {
                            try {
                                // TODO(rfink): The try/catch is a bit odd here.
                                return stringToStringDeserializer.deserialize(response);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to deserialize response", e);
                            }
                        },
                        MoreExecutors.directExecutor());
            }

            @Override
            public ListenableFuture<Void> voidToVoid() {
                Request request = Request.builder().build();

                Call call = channel.createCall(VOID_TO_VOID, request);
                return Futures.transform(
                        Calls.toFuture(call),
                        response -> {
                            try {
                                voidToVoidDeserializer.deserialize(response);
                                return null;
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to deserialize response", e);
                            }
                        },
                        MoreExecutors.directExecutor());
            }
        };
    }
}
