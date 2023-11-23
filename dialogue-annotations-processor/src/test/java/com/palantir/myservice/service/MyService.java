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
import com.google.errorprone.annotations.MustBeClosed;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.annotations.Request;
import com.palantir.tokens.auth.AuthHeader;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

// Annotation processor is not invoked directly here.
// @DialogueService(MyServiceDialogueServiceFactory.class)
@SuppressWarnings("TooManyArguments")
public interface MyService {

    @Request(method = HttpMethod.POST, path = "/greet")
    String greet(
            AuthHeader authHeader,
            // Idea is that Json.class provides the encoder to transform the
            // greeting string into application/json. Another hand-written
            // CustomJson.class may provide a custom ObjectMapper, but we
            // should make it relatively easy to implement this sort of thing.
            @Request.Body String greeting);

    // Support blocking and listenablefuture based on the return type
    @Request(method = HttpMethod.GET, path = "/greeting", accept = CustomStringDeserializer.class)
    ListenableFuture<String> getGreetingAsync();

    @MustBeClosed
    @Request(method = HttpMethod.GET, path = "/input-stream")
    InputStream inputStream();

    @MustBeClosed
    @Request(method = HttpMethod.GET, path = "/input-stream-custom", accept = CustomInputStreamDeserializer.class)
    InputStream customInputStream();

    // No decoders allowed (void method)
    // No encoders allowed (RequestBody is pre-encoded)
    @Request(method = HttpMethod.PUT, path = "/custom/request")
    void customRequest(EmptyRequestBody requestBody);

    // No decoders allowed (Response is raw)
    // Unclear: If the response status is non-200, do we throw?
    // No encoders allowed (no body)
    // Should we support custom static request headers via
    // method level annotations? e.g.
    // @Request.Header(name="Accept", value="text/plain")
    // This is the dialogue Response object
    @MustBeClosed
    @Request(method = HttpMethod.PUT, path = "/custom/request1")
    Response customResponse();

    @Request(method = HttpMethod.POST, path = "/params/{path1}/{path2}")
    void params(
            @Request.QueryParam("q1") String query1,
            @Request.QueryParam("q2") Optional<String> query2,
            @Request.QueryParam("q3") OptionalInt query3,
            @Request.QueryParam("q4") OptionalLong query4,
            @Request.QueryParam("q5") OptionalDouble query5,
            @Request.QueryParam("q6") List<String> query6,
            @Request.QueryParam("q7") MyAliasType query7,
            @Request.QueryParam(value = "q8", encoder = MyCustomTypeParamEncoder.class) MyCustomType query8,
            @Request.QueryParam(value = "q9", encoder = MyCustomTypeParamEncoder.class) Optional<MyCustomType> query9,
            @Request.QueryParam(value = "q10", encoder = MyCustomStringParamEncoder.class) String query10,
            @Request.QueryParam(value = "q11", encoder = MyCustomStringParamEncoder.class) Optional<String> query11,
            @Request.PathParam UUID path1,
            @Request.PathParam(encoder = MyCustomTypeParamEncoder.class) MyCustomType path2,
            @Request.Header("h1") String header1,
            @Request.Header("h2") Optional<String> header2,
            @Request.Header("h3") OptionalInt header3,
            @Request.Header("h4") OptionalLong header4,
            @Request.Header("h5") OptionalDouble header5,
            @Request.Header("h6") List<String> header6,
            @Request.Header("h7") MyAliasType header7,
            @Request.Header(value = "h8", encoder = MyCustomTypeParamEncoder.class) MyCustomType header8,
            @Request.Header(value = "h9", encoder = MyCustomTypeParamEncoder.class) Optional<MyCustomType> header9,
            @Request.Header(value = "h10", encoder = MyCustomStringParamEncoder.class) String header10,
            @Request.Header(value = "h11", encoder = MyCustomStringParamEncoder.class) Optional<String> header11,
            @Request.Body(MySerializableTypeBodySerializer.class) MySerializableType body);
}
