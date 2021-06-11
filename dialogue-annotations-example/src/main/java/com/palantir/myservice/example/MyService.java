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

package com.palantir.myservice.example;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.DialogueService;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.annotations.ErrorDecoder;
import com.palantir.dialogue.annotations.MapToMultimapParamEncoder;
import com.palantir.dialogue.annotations.Request;
import com.palantir.myservice.example.PutFileRequest.PutFileRequestSerializer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

@DialogueService(MyServiceDialogueServiceFactory.class)
public interface MyService {

    @Request(method = HttpMethod.POST, path = "/greet")
    String greet(
            // Idea is that Json.class provides the encoder to transform the
            // greeting string into application/json. Another hand-written
            // CustomJson.class may provide a custom ObjectMapper, but we
            // should make it relatively easy to implement this sort of thing.
            @Request.Body String greeting);

    // Support blocking and listenablefuture based on the return type
    @Request(method = HttpMethod.GET, path = "/greeting", accept = CustomStringDeserializer.class)
    ListenableFuture<String> getGreetingAsync();

    // No decoders allowed (void method)
    // No encoders allowed (RequestBody is pre-encoded)
    @Request(method = HttpMethod.PUT, path = "/custom/request")
    void customRequest(RequestBody requestBody);

    // No decoders allowed (Response is raw)
    // Unclear: If the response status is non-200, do we throw?
    // No encoders allowed (no body)
    // Should we support custom static request headers via
    // method level annotations? e.g.
    // @Request.Header(name="Accept", value="text/plain")
    // This is the dialogue Response object
    @Request(
            method = HttpMethod.PUT,
            path = "/custom/request1",
            accept = MyResponseDeserializer.class,
            errorDecoder = ErrorDecoder.None.class)
    Response customResponse();

    @Request(method = HttpMethod.PUT, path = "/custom/request2", errorDecoder = AlwaysThrowErrorDecoder.class)
    void customVoidErrorDecoder();

    @Request(method = HttpMethod.POST, path = "/params/{myPathParam}/{myPathParam2}")
    void params(
            @Request.QueryParam("q") String query,
            // Lists of primitive types are supported for @QueryParam and @Header
            @Request.QueryParam("q1") List<String> query1,
            // Path parameter variable name must match the request path component
            @Request.PathParam UUID myPathParam,
            @Request.PathParam(encoder = MyCustomParamTypeEncoder.class) MyCustomParamType myPathParam2,
            @Request.Header("Custom-Header") int requestHeaderValue,
            // Headers can be optional
            @Request.Header("Custom-Optional-Header") OptionalInt maybeRequestHeaderValue,
            // Optional lists of primitives are supported too!
            @Request.Header("Custom-Optional-Header1") Optional<List<Integer>> maybeRequestHeaderValue1,
            // Can supply a map to fill in arbitrary query values
            @Request.QueryMap(encoder = MapToMultimapParamEncoder.class) Map<String, String> queryParams,
            // Custom encoding classes may be provided for the request and response.
            // JSON should be easiest (default?).
            // By changing this to MySpecialJson.class you can have
            // it's own object mapper; this is same as BodySerDe in dialogue
            @Request.Body(MySerializableTypeBodySerializer.class) MySerializableType body);

    @Request(method = HttpMethod.POST, path = "/multipart")
    void multipart(@Request.Body(PutFileRequestSerializer.class) PutFileRequest request);
}
