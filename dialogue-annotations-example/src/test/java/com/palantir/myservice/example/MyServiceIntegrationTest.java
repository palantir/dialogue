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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.annotations.ContentBody;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.refreshable.Refreshable;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.OptionalAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class MyServiceIntegrationTest {

    private Undertow undertow;

    @FunctionalInterface
    interface TestHandler {
        void handleRequest(TestExchange exchange) throws Exception;
    }

    private TestHandler undertowHandler;

    private MyService myServiceDialogue;

    @BeforeEach
    public void beforeEach() {
        undertow = Undertow.builder()
                .addHttpListener(
                        0,
                        "localhost",
                        new BlockingHandler(exchange -> undertowHandler.handleRequest(new TestExchange(exchange))))
                .build();
        undertow.start();

        ServiceConfiguration config = ServiceConfiguration.builder()
                .addUris(getUri(undertow))
                .security(TestConfigurations.SSL_CONFIG)
                .readTimeout(Duration.ofSeconds(1))
                .writeTimeout(Duration.ofSeconds(1))
                .connectTimeout(Duration.ofSeconds(1))
                .build();

        DialogueClients.ReloadingFactory factory = DialogueClients.create(
                        Refreshable.only(ServicesConfigBlock.builder().build()))
                .withUserAgent(TestConfigurations.AGENT);
        myServiceDialogue = factory.getNonReloading(MyService.class, config);
    }

    @AfterEach
    public void afterEach() {
        undertow.stop();
    }

    @Test
    public void testGreet() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.POST);
            exchange.assertPath("/greet");
            exchange.assertAccept().isEqualTo("application/json");
            exchange.assertContentType().isEqualTo("application/json");

            exchange.exchange.setStatusCode(200);
            exchange.setContentType("application/json");
            exchange.writeStringBody("\"Hello\"");
        };
        assertThat(myServiceDialogue.greet("Hello")).isEqualTo("Hello");
    }

    @Test
    public void testGetGreetingAsync() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/greeting");
            exchange.assertAccept().isEqualTo("text/csv");
            exchange.assertContentType().isNull();
            exchange.assertNoBody();

            exchange.exchange.setStatusCode(200);
            exchange.setContentType("text/csv");
            exchange.writeStringBody("mystring,\"Hello\"");
        };
        assertThat(Futures.getUnchecked(myServiceDialogue.getGreetingAsync())).isEqualTo("\"Hello\"");
    }

    @Test
    public void testGetGreetingAsyncUnknownRemoteException() {
        String errorBody = "Error";
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/greeting");
            exchange.assertAccept().isEqualTo("text/csv");
            exchange.assertContentType().isNull();
            exchange.assertNoBody();

            exchange.exchange.setStatusCode(500);
            exchange.setContentType("text/plain");
            exchange.writeStringBody(errorBody);
        };

        assertThatThrownBy(() -> myServiceDialogue.getGreetingAsync().get())
                .isExactlyInstanceOf(ExecutionException.class)
                .hasCauseExactlyInstanceOf(UnknownRemoteException.class)
                .satisfies(executionException -> assertThat(
                                ((UnknownRemoteException) executionException.getCause()).getBody())
                        .isEqualTo(errorBody));
    }

    @Test
    public void testGetGreetingAsyncRemoteException() {
        String errorBody = "{\"errorCode\":\"FAILED_PRECONDITION\",\"errorName\":\"Default:FailedPrecondition\","
                + "\"errorInstanceId\":\"839ccac1-3944-479a-bcd2-3196b5fa16ee\",\"parameters\":{\"key\":\"value\"}}";
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/greeting");
            exchange.assertAccept().isEqualTo("text/csv");
            exchange.assertContentType().isNull();
            exchange.assertNoBody();

            exchange.exchange.setStatusCode(500);
            exchange.setContentType("application/json");
            exchange.writeStringBody(errorBody);
        };

        assertThatThrownBy(() -> myServiceDialogue.getGreetingAsync().get())
                .isExactlyInstanceOf(ExecutionException.class)
                .hasCauseExactlyInstanceOf(RemoteException.class)
                .satisfies(executionException -> {
                    RemoteException remoteException = (RemoteException) executionException.getCause();
                    assertThat(remoteException.getError())
                            .isEqualTo(SerializableError.builder()
                                    .errorCode("FAILED_PRECONDITION")
                                    .errorName("Default:FailedPrecondition")
                                    .errorInstanceId("839ccac1-3944-479a-bcd2-3196b5fa16ee")
                                    .putParameters("key", "value")
                                    .build());
                });
    }

    @Test
    public void testInputStream() throws IOException {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/input-stream");
            exchange.assertAccept().isEqualTo("application/octet-stream");
            exchange.assertContentType().isNull();

            exchange.exchange.setStatusCode(200);
            exchange.setContentType("application/octet-stream");
            exchange.writeStringBody("Hello");
        };
        try (InputStream inputStream = myServiceDialogue.inputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("Hello");
        }
    }

    @Test
    public void testCustomRequest() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.PUT);
            exchange.assertPath("/custom/request");
            exchange.assertAccept().isNull();
            exchange.assertContentType().isEqualTo("text/plain");
            exchange.assertBodyUtf8().isEqualTo("Hello, World");

            exchange.exchange.setStatusCode(204);
        };

        myServiceDialogue.customRequest(new RequestBody() {
            @Override
            public void writeTo(OutputStream output) throws IOException {
                output.write("Hello, World".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public boolean repeatable() {
                return true;
            }

            @Override
            public void close() {}
        });
    }

    @Test
    public void testCustomVoidErrorDecoder() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.PUT);
            exchange.assertPath("/custom/request2");
            exchange.assertAccept().isNull();
            exchange.assertContentType().isNull();
            exchange.assertBodyUtf8().isEmpty();

            exchange.exchange.setStatusCode(204);
        };

        assertThatThrownBy(myServiceDialogue::customVoidErrorDecoder)
                .isExactlyInstanceOf(SafeRuntimeException.class)
                .hasMessage("There are only errors");
    }

    @Test
    public void testCustomResponse200() {
        testCustomResponse(200);
    }

    @Test
    public void testCustomResponse500() {
        testCustomResponse(500);
    }

    @Test
    public void testParamsWithCustomHeader() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.POST);
            exchange.assertPath("/params/90a8481a-2ef5-4c64-83fc-04a9b369e2b8/my-custom-param-value");

            assertThat(exchange.exchange.getQueryParameters()).containsOnlyKeys("q1", "q2", "q3", "q4", "varq1");
            assertThat(exchange.exchange.getQueryParameters().get("q1")).containsOnly("query1");
            assertThat(exchange.exchange.getQueryParameters().get("q2")).containsOnly("query2-1", "query2-2");
            assertThat(exchange.exchange.getQueryParameters().get("q3")).containsOnly("query3");
            assertThat(exchange.exchange.getQueryParameters().get("q4")).containsOnly("query4");
            assertThat(exchange.exchange.getQueryParameters().get("varq1")).containsOnly("varvar1");
            exchange.assertAccept().isNull();
            exchange.assertContentType().isEqualTo("application/json");
            exchange.assertSingleValueHeader(HttpString.tryFromString("h1")).isEqualTo("header1");
            exchange.assertMultiValueHeader("h2")
                    .hasValueSatisfying(values -> assertThat(values).containsExactly("header2-1", "header2-2"));
            exchange.assertSingleValueHeader(HttpString.tryFromString("h3")).isEqualTo("header3");
            exchange.assertSingleValueHeader(HttpString.tryFromString("h4")).isEqualTo("header4");
            exchange.assertBodyUtf8().isEqualTo("{\n  \"value\" : \"my-serializable-type-value\"\n}");

            exchange.exchange.setStatusCode(200);
            exchange.exchange
                    .getResponseHeaders()
                    .add(HttpString.tryFromString("My-Custom-Header"), "my-custom-header-value");
            exchange.setContentType("text/csv");
            exchange.writeStringBody("Custom Body");
        };

        UUID uuid = UUID.fromString("90a8481a-2ef5-4c64-83fc-04a9b369e2b8");
        myServiceDialogue.params(
                "query1",
                Arrays.asList("query2-1", "query2-2"),
                Optional.of("query3"),
                ImmutableMyAliasType.of("query4"),
                uuid,
                new MyCustomType("my-custom-param-value"),
                "header1",
                Arrays.asList("header2-1", "header2-2"),
                Optional.of("header3"),
                ImmutableMyAliasType.of("header4"),
                ImmutableMap.of("varq1", "varvar1"),
                ImmutableMySerializableType.of("my-serializable-type-value"));
    }

    @Test
    public void testMultipart(@TempDir Path directory) throws IOException {

        String fileContentType = "text/plain; charset=UTF-8";
        String fileContent = "Hello World!";
        Path fileTxt = directory.resolve("file.txt");
        Files.writeString(fileTxt, fileContent, StandardCharsets.UTF_8);

        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.POST);
            exchange.assertPath("/multipart");
            exchange.assertAccept().isNull();
            exchange.assertContentType().startsWith("multipart/form-data; charset=ISO-8859-1;");

            try (FormDataParser parser = FormParserFactory.builder().build().createParser(exchange.exchange)) {
                FormData formData = parser.parseBlocking();
                assertThat(formData).hasSize(1);

                Deque<FormValue> typedFile = formData.get("typedFile");
                assertThat(typedFile).hasSize(1);
                FormValue onlyElement = Iterables.getOnlyElement(typedFile);
                assertThat(onlyElement.getHeaders().get(Headers.CONTENT_TYPE)).containsExactly(fileContentType);
                assertThat(onlyElement.getValue()).isEqualTo(fileContent);
            }

            exchange.exchange.setStatusCode(204);
        };

        myServiceDialogue.multipart(ImmutablePutFileRequest.builder()
                .contentBody(ContentBody.path(fileContentType, fileTxt))
                .build());
    }

    @Test
    void testMultiparams() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);

            assertThat(exchange.exchange.getQueryParameters().get("q1")).containsExactly("var1", "var2");
            assertThat(exchange.exchange.getQueryParameters().get("value-from-multimap"))
                    .containsExactly("var3");
        };

        myServiceDialogue.multiParams(
                ImmutableMultimap.<String, String>builder()
                        .putAll("q1", "var1", "var2")
                        .build(),
                new MyCustomType("var3"));
    }

    @Test
    void testMultiplePathParams() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/multipath/" + first + '/' + second);
        };
        myServiceDialogue.multiplePathSegments(ImmutableList.of(first, second));
    }

    @Test
    void testMultiplePathParams_empty() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/multipath");
        };
        myServiceDialogue.multiplePathSegments(ImmutableList.of());
    }

    @Test
    void testMultiplePathParams_escaped() {
        String first = "a/b";
        String second = "c/d";
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            // The server should receive uri-encoded slashes as '%2F' as opposed to
            // splitting the input segment string values into sub-segments. This allows
            // the server to recreate the original data.
            exchange.assertPath("/multipath-strings/a%2Fb/c%2Fd");
        };
        myServiceDialogue.multipleStringPathSegments(ImmutableList.of(first, second));
    }

    @Test
    void testMultiplePathParams_customEncoder() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            // The encoder is splitting on : & producing path segments for each part
            exchange.assertPath("/multipath-strings/a/b/c/d");
        };
        myServiceDialogue.multipleStringPathSegmentsUsingCustomEncoder("a:b:c:d");
    }

    @Test
    void testMultiplePathParams_customEncoder_empty() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            exchange.assertPath("/multipath-strings/");
        };
        myServiceDialogue.multipleStringPathSegmentsUsingCustomEncoder("");
    }

    @Test
    void testMultiplePathParams_customEncoder_odd() {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.GET);
            // The encoder is splitting on : & producing path segments for each part
            exchange.assertPath("/multipath-strings/a//b%2Fc");
        };
        myServiceDialogue.multipleStringPathSegmentsUsingCustomEncoder("a::b/c");
    }

    private void testCustomResponse(int code) {
        undertowHandler = exchange -> {
            exchange.assertMethod(HttpMethod.PUT);
            exchange.assertPath("/custom/request1");
            exchange.assertAccept().isEqualTo("*/*");
            exchange.assertContentType().isNull();
            exchange.assertNoBody();

            exchange.exchange.setStatusCode(code);
            exchange.exchange
                    .getResponseHeaders()
                    .add(HttpString.tryFromString("My-Custom-Header"), "my-custom-header-value");
            exchange.setContentType("text/csv");
            exchange.writeStringBody("Custom Body");
        };

        try (Response response = myServiceDialogue.customResponse()) {
            assertThat(response.code()).isEqualTo(code);
            assertThat(CharStreams.toString(new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
                    .isEqualTo("Custom Body");
            assertThat(response.headers().get("My-Custom-Header")).containsExactly("my-custom-header-value");
        } catch (IOException e) {
            throw new SafeRuntimeException(e);
        }
    }

    private static final class TestExchange {
        private final HttpServerExchange exchange;

        private TestExchange(HttpServerExchange exchange) {
            this.exchange = exchange;
        }

        public void assertMethod(HttpMethod method) {
            assertThat(exchange.getRequestMethod()).isEqualTo(HttpString.tryFromString(method.toString()));
        }

        public void assertPath(String path) {
            assertThat(exchange.getRelativePath()).isEqualTo(path);
        }

        public AbstractStringAssert<?> assertAccept() {
            return assertSingleValueHeader(Headers.ACCEPT);
        }

        public AbstractStringAssert<?> assertContentType() {
            return assertSingleValueHeader(Headers.CONTENT_TYPE);
        }

        public AbstractStringAssert<?> assertSingleValueHeader(HttpString header) {
            HeaderValues headerValues = exchange.getRequestHeaders().get(header);
            if (headerValues == null) {
                return assertThat((String) null);
            }
            assertThat(headerValues).hasSizeBetween(0, 1);
            return assertThat(headerValues.isEmpty() ? null : headerValues.getFirst());
        }

        public OptionalAssert<List<String>> assertMultiValueHeader(String header) {
            HeaderValues headerValues = exchange.getRequestHeaders().get(header);
            if (headerValues == null) {
                return assertThat(Optional.empty());
            }
            return assertThat(Optional.of(headerValues));
        }

        public AbstractStringAssert<?> assertBodyUtf8() {
            try {
                return assertThat(
                        CharStreams.toString(new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new SafeRuntimeException(e);
            }
        }

        public void assertNoBody() {
            assertThat(exchange.getInputStream()).isEmpty();
        }

        public void setContentType(String contentType) {
            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, contentType);
        }

        public void writeStringBody(String body) {
            try (PrintWriter p = new PrintWriter(exchange.getOutputStream(), true, StandardCharsets.UTF_8)) {
                p.print(body);
            }
        }
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }
}
