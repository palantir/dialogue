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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.Preconditions;
import com.palantir.ri.ResourceIdentifier;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// Example of the implementation code conjure would generate for a simple SampleService.
public final class SampleServiceClient {

    private SampleServiceClient() {}

    public String apiVersion() {
        return "0.0.0";
    }

    private static final Endpoint STRING_TO_STRING = new Endpoint() {
        private final PathTemplate pathTemplate = PathTemplate.builder()
                .fixed("objectToObject")
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

        @Override
        public String serviceName() {
            return "SampleService";
        }

        @Override
        public String endpointName() {
            return "STRING_TO_STRING";
        }

        @Override
        public String version() {
            return "1.0.0";
        }
    };

    private static final Endpoint VOID_TO_VOID = new Endpoint() {
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
            return "VOID_TO_VOID";
        }

        @Override
        public String version() {
            return "1.0.0";
        }
    };

    /**
     * Returns a new blocking {@link SampleService} implementation whose calls are executed on the given channel.
     * The {@code callTimeout} parameter indicates the maximum end-to-end life time for the blocking methods in this
     * service; an exception is thrown when this duration is exceeded.
     */
    // TODO(rfink): Consider using a builder pattern to construct clients
    public static SampleService blocking(Channel channel, ConjureRuntime runtime) {
        return new SampleService() {
            private Serializer<SampleObject> sampleObjectToSampleObjectSerializer =
                    runtime.bodySerDe().serializer(new TypeMarker<SampleObject>() {});
            private Deserializer<SampleObject> sampleObjectToSampleObjectDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<SampleObject>() {});
            private Deserializer<Void> voidToVoidDeserializer =
                    runtime.bodySerDe().emptyBodyDeserializer();
            private PlainSerDe plainSerDe = runtime.plainSerDe();

            @Override
            public SampleObject objectToObject(
                    String path, OffsetDateTime header, List<ResourceIdentifier> query, SampleObject body) {
                Preconditions.checkNotNull(path, "objectId parameter must not be null");
                Preconditions.checkNotNull(header, "header parameter must not be null");
                Preconditions.checkNotNull(body, "body parameter must not be null");
                Request request = Request.builder()
                        .putPathParams("objectId", plainSerDe.serializeString(path))
                        .putHeaderParams("headerKey", plainSerDe.serializeDateTime(header))
                        .putAllQueryParams("queryKey", plainSerDe.serializeRidList(query))
                        .body(sampleObjectToSampleObjectSerializer.serialize(body))
                        .build();
                return runtime.clients()
                        .block(runtime.clients()
                                .call(channel, STRING_TO_STRING, request, sampleObjectToSampleObjectDeserializer));
            }

            @Override
            public void voidToVoid() {
                Request request = Request.builder().build();
                runtime.clients().block(runtime.clients().call(channel, VOID_TO_VOID, request, voidToVoidDeserializer));
            }
        };
    }

    /**
     * Returns a new asynchronous {@link AsyncSampleService} implementation whose calls are executed on the given
     * channel.
     */
    // TODO(rfink): Consider using a builder pattern to construct clients
    public static AsyncSampleService async(Channel channel, ConjureRuntime runtime) {
        return new AsyncSampleService() {
            private Serializer<SampleObject> sampleObjectToSampleObjectSerializer =
                    runtime.bodySerDe().serializer(new TypeMarker<SampleObject>() {});
            private Deserializer<SampleObject> sampleObjectToSampleObjectDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<SampleObject>() {});
            private Deserializer<Void> voidToVoidDeserializer =
                    runtime.bodySerDe().emptyBodyDeserializer();
            private PlainSerDe plainSerDe = runtime.plainSerDe();

            @Override
            public ListenableFuture<SampleObject> stringToString(
                    String objectId, OffsetDateTime header, List<ResourceIdentifier> query, SampleObject body) {
                Preconditions.checkNotNull(objectId, "objectId parameter must not be null");
                Preconditions.checkNotNull(header, "header parameter must not be null");
                Preconditions.checkNotNull(body, "body parameter must not be null");
                Request request = Request.builder()
                        .putPathParams("objectId", plainSerDe.serializeString(objectId))
                        .putHeaderParams("headerKey", plainSerDe.serializeDateTime(header))
                        .putAllQueryParams("queryKey", plainSerDe.serializeRidList(query))
                        .body(sampleObjectToSampleObjectSerializer.serialize(body))
                        .build();
                return runtime.clients()
                        .call(channel, STRING_TO_STRING, request, sampleObjectToSampleObjectDeserializer);
            }

            @Override
            public ListenableFuture<Void> voidToVoid() {
                Request request = Request.builder().build();
                return runtime.clients().call(channel, VOID_TO_VOID, request, voidToVoidDeserializer);
            }
        };
    }
}
