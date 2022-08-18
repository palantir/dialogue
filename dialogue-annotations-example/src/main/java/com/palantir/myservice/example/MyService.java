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

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.MustBeClosed;
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

    // No decoders allowed (Response is raw); Must be annotated with @MustBeClosed
    // Unclear: If the response status is non-200, do we throw?
    // No encoders allowed (no body)
    // Should we support custom static request headers via
    // method level annotations? e.g.
    // @Request.Header(name="Accept", value="text/plain")
    // This is the dialogue Response object
    @MustBeClosed
    @Request(method = HttpMethod.PUT, path = "/custom/request1", errorDecoder = ErrorDecoder.None.class)
    Response customResponse();

    @Request(method = HttpMethod.PUT, path = "/custom/request2", errorDecoder = AlwaysThrowErrorDecoder.class)
    void customVoidErrorDecoder();

    @SuppressWarnings("TooManyArguments")
    @Request(method = HttpMethod.POST, path = "/params/{path1}/{path2}")
    void params(
            @Request.QueryParam("q1") String query1,
            // Lists of primitive types are supported for @QueryParam and @Header
            @Request.QueryParam("q2") List<String> query2,
            // Optionals of primitive types are supported for @QueryParam and @Header
            @Request.QueryParam("q3") Optional<String> query3,
            // Alias types are supported for @QueryParam and @Header
            @Request.QueryParam("q4") MyAliasType query4,
            // Path parameter variable name must match the request path component
            @Request.PathParam UUID path1,
            @Request.PathParam(encoder = MyCustomTypeParamEncoder.class) MyCustomType path2,
            @Request.Header("h1") String header1,
            @Request.Header("h2") List<String> header2,
            @Request.Header("h3") Optional<String> header3,
            @Request.Header("h4") MyAliasType header4,
            // Can supply a map to fill in arbitrary query values
            @Request.QueryMap(encoder = MapToMultimapParamEncoder.class) Map<String, String> queryParams,
            // Custom encoding classes may be provided for the request and response.
            // JSON should be easiest (default?).
            // By changing this to MySpecialJson.class you can have
            // it's own object mapper; this is same as BodySerDe in dialogue
            @Request.Body(MySerializableTypeBodySerializer.class) MySerializableType body);

    @Request(method = HttpMethod.GET, path = "/multiparams")
    void multiParams(
            // or you can supply a multimap directly
            @Request.QueryMap Multimap<String, String> multiQueryParams,
            // or you can supply a custom converter
            @Request.QueryMap(encoder = MyCustomMultimapEncoder.class) MyCustomType myParamToMultimap);

    @Request(method = HttpMethod.POST, path = "/multipart")
    void multipart(@Request.Body(PutFileRequestSerializer.class) PutFileRequest request);

    @Request(method = HttpMethod.GET, path = "/multipath/{pathSegments}")
    void multiplePathSegments(@Request.PathParam List<UUID> pathSegments);

    @Request(method = HttpMethod.GET, path = "/multipath-strings/{pathSegments}")
    void multipleStringPathSegments(@Request.PathParam List<String> pathSegments);

    @Request(method = HttpMethod.GET, path = "/multipath-strings/{pathSegments}")
    void multipleStringPathSegmentsUsingCustomEncoder(
            @Request.PathParam(listEncoder = MyCustomPathSegmentEncoder.class) String pathSegments);
}
