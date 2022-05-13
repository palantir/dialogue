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
import com.palantir.dialogue.annotations.ToStringParamEncoder;
import com.palantir.tokens.auth.AuthHeader;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

    @Request(method = HttpMethod.POST, path = "/params/{myPathParam}/{myPathParam2}")
    void params(
            @Request.QueryParam("q") String query,
            @Request.QueryParam(value = "q1", encoder = MyCustomParamTypeParameterEncoder.class)
                    MyCustomParamType query1,
            @Request.QueryParam(value = "q2", encoder = MyCustomParamTypeParameterEncoder.class)
                    Optional<MyCustomParamType> query2,
            @Request.QueryParam(value = "q3", encoder = MyCustomStringParameterEncoder.class) String query3,
            @Request.QueryParam(value = "q4", encoder = MyCustomStringParameterEncoder.class) Optional<String> query4,
            @Request.QueryParam(value = "q5") List<String> query5,
            @Request.QueryParam(value = "q6") Optional<List<String>> query6,
            // Path parameter variable name must match the request path component
            @Request.PathParam UUID myPathParam,
            @Request.PathParam(encoder = MyCustomParamTypeParameterEncoder.class) MyCustomParamType myPathParam2,
            @Request.Header("Custom-Header") int requestHeaderValue,
            // Headers can be optional
            @Request.Header("Custom-Optional-Header1") Optional<String> maybeCustomOptionalHeader1Value,
            @Request.Header("Custom-Optional-Header2") OptionalInt maybeCustomOptionalHeader2Value,
            @Request.Header(value = "Custom-Optional-Header3", encoder = MyCustomParamTypeParameterEncoder.class)
                    Optional<MyCustomParamType> maybeCustomOptionalHeader3Value,
            @Request.Header("Custom-Header1") List<String> customListHeader,
            @Request.Header("Custom-Optional-Header3") Optional<List<String>> customOptionalListHeader,
            @Request.Header(value = "Custom-To-String-Header", encoder = ToStringParamEncoder.class)
                    BigInteger bigInteger,
            // Custom encoding classes may be provided for the request and response.
            // JSON should be easiest (default?).
            // By changing this to MySpecialJson.class you can have
            // it's own object mapper; this is same as BodySerDe in dialogue
            @Request.Body(MySerializableTypeBodySerializer.class) MySerializableType body);
}
