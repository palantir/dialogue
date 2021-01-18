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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.example.AliasOfAliasOfOptional;
import com.palantir.dialogue.example.AliasOfOptional;
import com.palantir.dialogue.example.SampleObject;
import com.palantir.ri.ResourceIdentifier;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;

@Generated("com.palantir.conjure.java.services.dialogue.DialogueInterfaceGenerator")
public interface SampleServiceAsyncButNotReally {
    /**
     * @apiNote {@code GET /voidToVoid}
     */
    ListenableFuture<Void> voidToVoid();

    /**
     * @apiNote {@code POST /objectToObject/objects/{path}}
     */
    ListenableFuture<SampleObject> objectToObject(
            OffsetDateTime header, String path, List<ResourceIdentifier> queryKey, SampleObject body);

    /**
     * @apiNote {@code GET /getOptionalBinary}
     */
    ListenableFuture<Optional<InputStream>> getOptionalBinary();

    /**
     * @apiNote {@code GET /getMyAlias}
     */
    ListenableFuture<AliasOfOptional> getMyAlias();

    /**
     * @apiNote {@code GET /getMyAlias2}
     */
    ListenableFuture<AliasOfAliasOfOptional> getMyAlias2();

    /**
     * Creates an asynchronous/non-blocking client for a SampleService service.
     */
    static SampleServiceAsyncButNotReally of(EndpointChannelFactory endpointChannelFactory, ConjureRuntime runtime) {
        return new SampleServiceAsyncButNotReally() {
            private final PlainSerDe plainSerDe = runtime.plainSerDe();

            private final EndpointChannel voidToVoidChannel =
                    endpointChannelFactory.endpoint(DialogueSampleEndpoints.voidToVoid);

            private final Deserializer<Void> voidToVoidDeserializer =
                    runtime.bodySerDe().emptyBodyDeserializer();

            private final Serializer<SampleObject> objectToObjectSerializer =
                    runtime.bodySerDe().serializer(new TypeMarker<SampleObject>() {});

            private final EndpointChannel objectToObjectChannel =
                    endpointChannelFactory.endpoint(DialogueSampleEndpoints.objectToObject);

            private final Deserializer<SampleObject> objectToObjectDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<SampleObject>() {});

            private final EndpointChannel getOptionalBinaryChannel =
                    endpointChannelFactory.endpoint(DialogueSampleEndpoints.getOptionalBinary);

            private final EndpointChannel getMyAliasChannel =
                    endpointChannelFactory.endpoint(DialogueSampleEndpoints.getMyAlias);

            private final Deserializer<AliasOfOptional> getMyAliasDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<AliasOfOptional>() {});

            private final EndpointChannel getMyAlias2Channel =
                    endpointChannelFactory.endpoint(DialogueSampleEndpoints.getMyAlias2);

            private final Deserializer<AliasOfAliasOfOptional> getMyAlias2Deserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<AliasOfAliasOfOptional>() {});

            @Override
            public ListenableFuture<Void> voidToVoid() {
                Request.Builder request = Request.builder();
                return runtime.clients().callBlocking(voidToVoidChannel, request.build(), voidToVoidDeserializer);
            }

            @Override
            public ListenableFuture<SampleObject> objectToObject(
                    OffsetDateTime header, String path, List<ResourceIdentifier> queryKey, SampleObject body) {
                Request.Builder request = Request.builder();
                request.putPathParams("path", plainSerDe.serializeString(path));
                request.putHeaderParams("HeaderKey", plainSerDe.serializeDateTime(header));
                for (ResourceIdentifier queryKeyElement : queryKey) {
                    request.putQueryParams("queryKey", plainSerDe.serializeRid(queryKeyElement));
                }
                request.body(objectToObjectSerializer.serialize(body));
                return runtime.clients()
                        .callBlocking(objectToObjectChannel, request.build(), objectToObjectDeserializer);
            }

            @Override
            public ListenableFuture<Optional<InputStream>> getOptionalBinary() {
                Request.Builder request = Request.builder();
                return runtime.clients()
                        .callBlocking(
                                getOptionalBinaryChannel,
                                request.build(),
                                runtime.bodySerDe().optionalInputStreamDeserializer());
            }

            @Override
            public ListenableFuture<AliasOfOptional> getMyAlias() {
                Request.Builder request = Request.builder();
                return runtime.clients().callBlocking(getMyAliasChannel, request.build(), getMyAliasDeserializer);
            }

            @Override
            public ListenableFuture<AliasOfAliasOfOptional> getMyAlias2() {
                Request.Builder request = Request.builder();
                return runtime.clients().callBlocking(getMyAlias2Channel, request.build(), getMyAlias2Deserializer);
            }

            @Override
            public String toString() {
                return "SampleServiceAsync{_endpointChannelFactory=" + endpointChannelFactory + ", runtime=" + runtime
                        + '}';
            }
        };
    }

    /**
     * Creates an asynchronous/non-blocking client for a SampleService service.
     */
    static SampleServiceAsyncButNotReally of(Channel channel, ConjureRuntime runtime) {
        if (channel instanceof EndpointChannelFactory) {
            return of((EndpointChannelFactory) channel, runtime);
        }
        return of(
                new EndpointChannelFactory() {
                    @Override
                    public EndpointChannel endpoint(Endpoint endpoint) {
                        return runtime.clients().bind(channel, endpoint);
                    }
                },
                runtime);
    }
}
