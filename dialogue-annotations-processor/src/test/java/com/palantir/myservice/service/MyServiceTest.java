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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.refreshable.Refreshable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class MyServiceTest {

    public final MockWebServer server = new MockWebServer();

    private MyService myServiceDialogue;

    @BeforeEach
    public void beforeEach() throws IOException {
        server.start();
        PartialServiceConfiguration partialServiceConfiguration = PartialServiceConfiguration.builder()
                .addUris(url("").url().toString())
                .build();
        ServicesConfigBlock scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices("myServiceDialogue", partialServiceConfiguration)
                .build();
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(Refreshable.create(scb)).withUserAgent(TestConfigurations.AGENT);
        myServiceDialogue = factory.get(MyService.class, "myServiceDialogue");
    }

    @AfterEach
    public void afterEach() throws IOException {
        server.close();
    }

    @Test
    public void testGreet() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("\"Hello\""));

        assertThat(myServiceDialogue.greet("Hello")).isEqualTo("Hello");

        assertRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(HttpMethod.POST.toString());
            assertThat(request.getRequestUrl()).isEqualTo(url("/greet"));
            assertThat(request.getHeader("Accept")).isEqualTo("application/json");
            assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        });
    }

    @Test
    public void testGetGreetingAsync() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/csv")
                .setBody("mystring,\"Hello\""));

        assertThat(Futures.getUnchecked(myServiceDialogue.getGreetingAsync())).isEqualTo("\"Hello\"");

        assertRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(HttpMethod.GET.toString());
            assertThat(request.getRequestUrl()).isEqualTo(url("/greeting"));
            assertThat(request.getHeader("Accept")).isEqualTo("text/csv");
            assertThat(request.getHeader("Content-Type")).isNull();
        });
    }

    @Test
    public void testCustomRequest() {
        server.enqueue(new MockResponse().setResponseCode(200));

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

        assertRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(HttpMethod.PUT.toString());
            assertThat(request.getRequestUrl()).isEqualTo(url("/custom/request"));
            assertThat(request.getHeader("Accept")).isNull();
            assertThat(request.getHeader("Content-Type")).isEqualTo("text/plain");
            assertThat(request.getBody().readUtf8()).isEqualTo("Hello, World");
        });
    }

    @Test
    public void testCustomResponse() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("My-Custom-Header", "my-custom-header-value"));

        try (Response response = myServiceDialogue.customResponse()) {
            assertThat(ByteStreams.exhaust(response.body())).isEqualTo(0L);
            assertThat(response.headers().get("My-Custom-Header")).containsExactly("my-custom-header-value");
        }

        assertRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(HttpMethod.PUT.toString());
            assertThat(request.getRequestUrl()).isEqualTo(url("/custom/request1"));
            assertThat(request.getHeader("Accept")).isNull();
            assertThat(request.getHeader("Content-Type")).isNull();
            assertThat(request.getBody().readUtf8()).isEqualTo("");
        });
    }

    private void assertRequest(Consumer<RecordedRequest> assertions) {
        try {
            assertions.accept(server.takeRequest());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SafeRuntimeException(e);
        }
    }

    private HttpUrl url(String subPath) {
        return server.url("/my-service-dialogue" + subPath);
    }
}
