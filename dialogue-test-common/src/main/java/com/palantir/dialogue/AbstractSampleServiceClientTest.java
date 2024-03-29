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

// CHECKSTYLE:OFF  // static import

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.example.SampleObject;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.ri.ResourceIdentifier;
import java.net.ConnectException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

// CHECKSTYLE:ON

@EnableRuleMigrationSupport
public abstract class AbstractSampleServiceClientTest {

    protected abstract SampleServiceBlocking createBlockingClient(URL baseUrl, Duration timeout);

    protected abstract SampleServiceAsync createAsyncClient(URL baseUrl, Duration timeout);

    private static final String PATH = "myPath";
    private static final OffsetDateTime HEADER = OffsetDateTime.parse("2018-07-19T08:11:21+00:00");
    private static final ImmutableList<ResourceIdentifier> QUERY =
            ImmutableList.of(ResourceIdentifier.of("ri.a.b.c.d"), ResourceIdentifier.of("ri.a.b.c.e"));
    private static final SampleObject BODY = SampleObject.of(42);
    private static final String BODY_STRING = "{\"intProperty\":42}";
    private static final SampleObject RESPONSE = SampleObject.of(84);
    private static final String RESPONSE_STRING = "{\"intProperty\": 84}";

    @Rule
    public final MockWebServer server = new MockWebServer();

    private SampleServiceBlocking blockingClient;
    private SampleServiceAsync asyncClient;

    @BeforeEach
    public void before() {
        server.useHttps(SslSocketFactories.createSslSocketFactory(TestConfigurations.SSL_CONFIG), false);
        blockingClient = createBlockingClient(server.url("").url(), Duration.ofSeconds(1));
        asyncClient = createAsyncClient(server.url("").url(), Duration.ofSeconds(1));
    }

    @Test
    public void testBlocking_objectToObject_expectedCase() throws Exception {
        server.enqueue(
                new MockResponse().setBody(RESPONSE_STRING).addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        assertThat(blockingClient.objectToObject(HEADER, PATH, QUERY, BODY)).isEqualTo(RESPONSE);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .isEqualTo("/objectToObject/objects/myPath?queryKey=ri.a.b.c.d&queryKey=ri.a.b.c.e");
        assertThat(request.getHeader("headerKey")).isEqualTo("2018-07-19T08:11:21Z");
        assertThat(request.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(BODY_STRING);
    }

    @Test
    public void testBlocking_objectToObject_nullRequestBody() {
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cannot serialize null value");
    }

    @Test
    public void testAsync_objectToObject_expectedCase() throws Exception {
        server.enqueue(
                new MockResponse().setBody(RESPONSE_STRING).addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        assertThat(asyncClient.objectToObject(HEADER, PATH, QUERY, BODY).get()).isEqualTo(RESPONSE);
    }

    @Test
    public void testBlocking_objectToObject_throwsWhenResponseBodyIsEmpty() {
        server.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, BODY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize response stream");
    }

    @Test
    public void testBlocking_voidToVoid_doesNotThrowWhenResponseBodyIsNonEmpty() {
        server.enqueue(new MockResponse().setBody("Unexpected response"));
        blockingClient.voidToVoid();
    }

    @Test
    public void testAsync_voidToVoid_doesNotThrowWhenResponseBodyIsNonEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("Unexpected response"));
        assertThat(asyncClient.voidToVoid().get()).isNull();
    }

    @Test
    public void testAsync_objectToObject_throwsWhenResponseBodyIsEmpty() {
        server.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        assertThatThrownBy(() ->
                        asyncClient.objectToObject(HEADER, PATH, QUERY, BODY).get())
                .hasMessageContaining("Failed to deserialize response");
    }

    @Test
    public void testAsync_objectToObject_nullRequestBody() {
        assertThatThrownBy(() ->
                        asyncClient.objectToObject(HEADER, PATH, QUERY, null).get())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cannot serialize null value");
    }

    @Test
    public void testBlocking_voidToVoid_expectedCase() throws Exception {
        server.enqueue(new MockResponse());
        blockingClient.voidToVoid();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/voidToVoid");
    }

    @Test
    public void testAsync_voidToVoid_expectedCase() throws Exception {
        server.enqueue(new MockResponse());
        assertThat(asyncClient.voidToVoid().get()).isNull();
    }

    @Test
    @Timeout(2)
    public void testBlocking_throwsOnConnectError() throws Exception {
        server.shutdown();
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, BODY))
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(ConnectException.class)
                .hasMessageMatching(".*((Connection refused)|(Failed to connect)).*");
    }

    @Test // see client construction: we set a 1s timeout
    @Timeout(5)
    public void testBlocking_throwsOnTimeout() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("\"response\"")
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBodyDelay(10, TimeUnit.SECONDS));
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, BODY))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @Timeout(2)
    public void testAsync_throwsOnConnectError() throws Exception {
        server.shutdown();
        assertThatThrownBy(() -> asyncClient.voidToVoid().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ConnectException.class)
                .hasMessageMatching(".*((Connection refused)|(Failed to connect)).*");
    }
}
