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

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.example.AliasOfAliasOfOptional;
import com.palantir.dialogue.example.AliasOfOptional;
import com.palantir.dialogue.example.SampleObject;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.ri.ResourceIdentifier;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;

@Generated("com.palantir.conjure.java.services.dialogue.DialogueInterfaceGenerator")
public interface SampleServiceReallyBlocking extends SampleServiceBlocking {
    /**
     * @apiNote {@code GET /voidToVoid}
     */
    void voidToVoid();

    /**
     * @apiNote {@code POST /objectToObject/objects/{path}}
     */
    SampleObject objectToObject(
            OffsetDateTime header, String path, List<ResourceIdentifier> queryKey, SampleObject body);

    /**
     * @apiNote {@code GET /getOptionalBinary}
     */
    Optional<InputStream> getOptionalBinary();

    /**
     * @apiNote {@code GET /getMyAlias}
     */
    AliasOfOptional getMyAlias();

    /**
     * @apiNote {@code GET /getMyAlias2}
     */
    AliasOfAliasOfOptional getMyAlias2();

    /**
     * Creates a synchronous/blocking client for a SampleService service.
     */
    static SampleServiceReallyBlocking of(EndpointChannelFactory endpointChannelFactory, ConjureRuntime runtime) {
        SampleServiceAsyncButNotReally delegate = SampleServiceAsyncButNotReally.of(endpointChannelFactory, runtime);
        return new SampleServiceReallyBlocking() {
            @Override
            public void voidToVoid() {
                runtime.clients().block(delegate.voidToVoid());
            }

            @Override
            public SampleObject objectToObject(
                    OffsetDateTime header, String path, List<ResourceIdentifier> queryKey, SampleObject body) {
                return runtime.clients().block(delegate.objectToObject(header, path, queryKey, body));
            }

            @Override
            public Optional<InputStream> getOptionalBinary() {
                return runtime.clients().block(delegate.getOptionalBinary());
            }

            @Override
            public AliasOfOptional getMyAlias() {
                return runtime.clients().block(delegate.getMyAlias());
            }

            @Override
            public AliasOfAliasOfOptional getMyAlias2() {
                return runtime.clients().block(delegate.getMyAlias2());
            }

            @Override
            public String toString() {
                return "SampleServiceBlocking{_endpointChannelFactory=" + endpointChannelFactory + ", runtime="
                        + runtime + '}';
            }
        };
    }

    /**
     * Creates an asynchronous/non-blocking client for a SampleService service.
     */
    static SampleServiceReallyBlocking of(Channel channel, ConjureRuntime runtime) {
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
