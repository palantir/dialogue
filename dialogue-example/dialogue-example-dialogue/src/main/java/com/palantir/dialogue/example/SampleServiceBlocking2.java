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

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.ri.ResourceIdentifier;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;

@Generated("com.palantir.conjure.java.services.dialogue.DialogueInterfaceGenerator")
public interface SampleServiceBlocking2 {
    void voidToVoid();

    SampleObject objectToObject(
            OffsetDateTime header, String path, List<ResourceIdentifier> queryKey, SampleObject body);

    Optional<InputStream> getOptionalBinary();

    AliasOfOptional getMyAlias();

    AliasOfAliasOfOptional getMyAlias2();

    /**
     * Creates a synchronous/blocking client for a SampleService service.
     */
    static SampleServiceBlocking2 of(Channel channel, ConjureRuntime runtime) {
        SampleServiceAsync delegate = SampleServiceAsync2.of(channel, runtime);
        return new SampleServiceBlocking2() {
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
                return "SampleServiceBlocking{channel=" + channel + ", runtime=" + runtime + '}';
            }
        };
    }
}
