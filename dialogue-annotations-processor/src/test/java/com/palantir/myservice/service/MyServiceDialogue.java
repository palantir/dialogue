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

package com.palantir.myservice.service;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.DialogueServiceFactory;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.annotations.Json;
import com.palantir.dialogue.annotations.RequestDeserializer;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

interface MyServiceDialogue extends DialogueServiceFactory<MyService> {

    @Override
    default MyService create(EndpointChannelFactory endpointChannelFactory, ConjureRuntime runtime) {
        return new MyService() {

            private final PlainSerDe plainSerDe = runtime.plainSerDe();
            private final BodySerDe bodySerDe = runtime.bodySerDe();
            private final Json json = new Json();

            private final EndpointChannel greetChannel = endpointChannelFactory.endpoint(Endpoints.greet);
            private final Serializer<String> greetBodySerializer = json.serializerFor(new TypeMarker<String>() {});
            private final Deserializer<String> greetBodyDeserializer =
                    json.deserializerFor(new TypeMarker<String>() {});

            private final EndpointChannel greetingAsyncChannel =
                    endpointChannelFactory.endpoint(Endpoints.greetingAsync);
            private final Deserializer<String> greetingAsyncDeserializer = new CustomStringDeserializer();

            private final EndpointChannel customRequestChannel =
                    endpointChannelFactory.endpoint(Endpoints.customRequest);
            private final Deserializer<Void> customRequestDeserializer = bodySerDe.emptyBodyDeserializer();

            private final EndpointChannel customResponseChannel =
                    endpointChannelFactory.endpoint(Endpoints.customResponse);
            private final Deserializer<Response> customResponseDeserializer = RequestDeserializer.INSTANCE;

            private final EndpointChannel paramsChannel = endpointChannelFactory.endpoint(Endpoints.params);
            private final Serializer<SerializableType> paramsBodySerializer = new SerializableTypeBodySerializer();
            private final Deserializer<Void> paramsDeserializer = bodySerDe.emptyBodyDeserializer();

            @Override
            public String greet(String body) {
                Request.Builder request = Request.builder();
                request.body(greetBodySerializer.serialize(body));
                return runtime.clients().callBlocking(greetChannel, request.build(), greetBodyDeserializer);
            }

            @Override
            public ListenableFuture<String> getGreetingAsync() {
                Request.Builder request = Request.builder();
                return runtime.clients().call(greetingAsyncChannel, request.build(), greetingAsyncDeserializer);
            }

            @Override
            public void customRequest(RequestBody requestBody) {
                Request.Builder request = Request.builder();
                request.body(requestBody);
                runtime.clients().callBlocking(customRequestChannel, request.build(), customRequestDeserializer);
            }

            @Override
            public Response customResponse() {
                Request.Builder request = Request.builder();
                return runtime.clients()
                        .callBlocking(customResponseChannel, request.build(), customResponseDeserializer);
            }

            @Override
            public void params(
                    String query,
                    UUID myPathParam,
                    MyCustomParamType myPathParam2,
                    int requestHeaderValue,
                    OptionalInt maybeRequestHeaderValue,
                    SerializableType body) {
                Request.Builder request = Request.builder();
                request.putQueryParams("q", query);
                request.putPathParams("myPathParam", plainSerDe.serializeUuid(myPathParam));
                request.putPathParams("myPathParam2", myPathParam2.valueOf());
                request.putHeaderParams("Custom-Header", plainSerDe.serializeInteger(requestHeaderValue));
                if (maybeRequestHeaderValue.isPresent()) {
                    request.putHeaderParams(
                            "Custom-Optional-Header", plainSerDe.serializeInteger(maybeRequestHeaderValue.getAsInt()));
                }
                request.body(paramsBodySerializer.serialize(body));
                runtime.clients().callBlocking(paramsChannel, request.build(), paramsDeserializer);
            }
        };
    }

    enum Endpoints implements Endpoint {
        greet {
            private final PathTemplate pathTemplate =
                    PathTemplate.builder().fixed("greet").build();

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
                return SERVICE_NAME;
            }

            @Override
            public String endpointName() {
                return "greet";
            }

            @Override
            public String version() {
                return VERSION;
            }
        },
        greetingAsync {
            private final PathTemplate pathTemplate =
                    PathTemplate.builder().fixed("greeting").build();

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
                return SERVICE_NAME;
            }

            @Override
            public String endpointName() {
                return "getGreetingAsync";
            }

            @Override
            public String version() {
                return VERSION;
            }
        },
        customRequest {
            private final PathTemplate pathTemplate =
                    PathTemplate.builder().fixed("custom").fixed("request").build();

            @Override
            public void renderPath(Map<String, String> params, UrlBuilder url) {
                pathTemplate.fill(params, url);
            }

            @Override
            public HttpMethod httpMethod() {
                return HttpMethod.PUT;
            }

            @Override
            public String serviceName() {
                return SERVICE_NAME;
            }

            @Override
            public String endpointName() {
                return "customResponse";
            }

            @Override
            public String version() {
                return VERSION;
            }
        },
        customResponse {
            private final PathTemplate pathTemplate =
                    PathTemplate.builder().fixed("custom").fixed("request1").build();

            @Override
            public void renderPath(Map<String, String> params, UrlBuilder url) {
                pathTemplate.fill(params, url);
            }

            @Override
            public HttpMethod httpMethod() {
                return HttpMethod.PUT;
            }

            @Override
            public String serviceName() {
                return SERVICE_NAME;
            }

            @Override
            public String endpointName() {
                return "customRequest";
            }

            @Override
            public String version() {
                return VERSION;
            }
        },
        params {
            private final PathTemplate pathTemplate = PathTemplate.builder()
                    .fixed("params")
                    .variable("myPathParam")
                    .variable("myPathParam2")
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
                return SERVICE_NAME;
            }

            @Override
            public String endpointName() {
                return "params";
            }

            @Override
            public String version() {
                return VERSION;
            }
        };

        private static final String SERVICE_NAME = "MyService";
        private static final String VERSION = Optional.ofNullable(
                        Endpoints.class.getPackage().getImplementationVersion())
                .orElse("0.0.0");
    }
}
