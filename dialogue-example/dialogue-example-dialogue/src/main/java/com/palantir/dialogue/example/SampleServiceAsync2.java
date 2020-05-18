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

package com.palantir.dialogue.example;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.ri.ResourceIdentifier;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Just a proof of concept before changing any codegen. */
public interface SampleServiceAsync2 extends SampleServiceAsync {
    @Override
    ListenableFuture<Void> voidToVoid();

    @Override
    ListenableFuture<SampleObject> objectToObject(
            OffsetDateTime header, String path, List<ResourceIdentifier> queryKey, SampleObject body);

    @Override
    ListenableFuture<Optional<InputStream>> getOptionalBinary();

    @Override
    ListenableFuture<AliasOfOptional> getMyAlias();

    @Override
    ListenableFuture<AliasOfAliasOfOptional> getMyAlias2();

    /**
     * Creates an asynchronous/non-blocking client for a SampleService service.
     */
    static SampleServiceAsync of(Channel channel, ConjureRuntime runtime) {
        return new SampleServiceAsync() {
            private final PlainSerDe plainSerDe = runtime.plainSerDe();

            // TODO(dfox): delete this entire class when conjure-java codegen produces the following:
            private final EndpointChannel voidToVoidChannel =
                    runtime.clients().bindEndpoint(channel, DialogueSampleEndpoints.voidToVoid);
            private final Deserializer<Void> voidToVoidDeserializer =
                    runtime.bodySerDe().emptyBodyDeserializer();

            private final EndpointChannel objectToObjectChannel =
                    runtime.clients().bindEndpoint(channel, DialogueSampleEndpoints.objectToObject);
            private final Serializer<SampleObject> objectToObjectSerializer =
                    runtime.bodySerDe().serializer(new TypeMarker<SampleObject>() {});
            private final Deserializer<SampleObject> objectToObjectDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<SampleObject>() {});

            private final EndpointChannel getMyAliasChannel =
                    runtime.clients().bindEndpoint(channel, DialogueSampleEndpoints.getMyAlias);
            private final Deserializer<AliasOfOptional> getMyAliasDeserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<AliasOfOptional>() {});

            private final EndpointChannel getMyAlias2Channel =
                    runtime.clients().bindEndpoint(channel, DialogueSampleEndpoints.getMyAlias);
            private final Deserializer<AliasOfAliasOfOptional> getMyAlias2Deserializer =
                    runtime.bodySerDe().deserializer(new TypeMarker<AliasOfAliasOfOptional>() {});

            private final EndpointChannel getOptionalBinaryChannel =
                    runtime.clients().bindEndpoint(channel, DialogueSampleEndpoints.getOptionalBinary);

            @Override
            public ListenableFuture<Void> voidToVoid() {
                Request.Builder request = Request.builder();
                return runtime.clients().call(voidToVoidChannel, request.build(), voidToVoidDeserializer);
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
                return runtime.clients().call(objectToObjectChannel, request.build(), objectToObjectDeserializer);
            }

            @Override
            public ListenableFuture<Optional<InputStream>> getOptionalBinary() {
                Request.Builder request = Request.builder();
                return runtime.clients()
                        .call(
                                getOptionalBinaryChannel,
                                request.build(),
                                runtime.bodySerDe().optionalInputStreamDeserializer());
            }

            @Override
            public ListenableFuture<AliasOfOptional> getMyAlias() {
                Request.Builder request = Request.builder();
                return runtime.clients().call(getMyAliasChannel, request.build(), getMyAliasDeserializer);
            }

            @Override
            public ListenableFuture<AliasOfAliasOfOptional> getMyAlias2() {
                Request.Builder request = Request.builder();
                return runtime.clients().call(getMyAlias2Channel, request.build(), getMyAlias2Deserializer);
            }

            @Override
            public String toString() {
                return "SampleServiceAsync2{channel=" + channel + ", runtime=" + runtime + '}';
            }
        };
    }
}
