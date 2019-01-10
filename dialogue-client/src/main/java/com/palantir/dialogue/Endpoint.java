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

package com.palantir.dialogue;

import java.util.Map;

/**
 * Defines a single HTTP-based RPC endpoint in terms of a {@link #renderPath path rendering} and {@link
 * #responseDeserializer response} and {@link #errorDecoder error} decoders.
 */
public interface Endpoint<ReqT, RespT> {
    String renderPath(Map<String, String> params);
    HttpMethod httpMethod();

    /**
     * The serializer used to render Java objects into HTTP body byte arrays. The serializer is typically invoked by
     * {@link Channel}s for any Request with non-empty {@link Request#body}.
     */
    Serializer<ReqT> requestSerializer();

    /**
     * The deserializer used to materialize Java objects from HTTP response body byte arrays. The deserializer is
     * typically invoked by {@link Channel}s on the body of any successful response, even if the response is empty.
     */
    Deserializer<RespT> responseDeserializer();
    OkHttpErrorDecoder errorDecoder();
}
