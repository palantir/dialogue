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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.StartStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// CHECKSTYLE:ON

@SuppressWarnings("VisibilityModifier")
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

    @StartStop
    protected final MockWebServer server = new MockWebServer();

    private SampleServiceBlocking blockingClient;
    private SampleServiceAsync asyncClient;

    @BeforeEach
    public void before() {
        server.useHttps(SslSocketFactories.createSslSocketFactory(TestConfigurations.SSL_CONFIG));
        blockingClient = createBlockingClient(server.url("").url(), Duration.ofSeconds(1));
        asyncClient = createAsyncClient(server.url("").url(), Duration.ofSeconds(1));
    }

    @Test
    public void testBlocking_objectToObject_expectedCase() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .body(RESPONSE_STRING)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());

        assertThat(blockingClient.objectToObject(HEADER, PATH, QUERY, BODY)).isEqualTo(RESPONSE);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getUrl().pathSegments()).containsExactly("objectToObject", "objects", "myPath");
        assertThat(request.getUrl().queryParameterNames()).containsExactlyInAnyOrder("queryKey");
        assertThat(request.getUrl().queryParameterValues("queryKey")).containsExactly("ri.a.b.c.d", "ri.a.b.c.e");
        assertThat(request.getHeaders().get("headerKey")).isEqualTo("2018-07-19T08:11:21Z");
        assertThat(request.getBody().utf8()).isEqualTo(BODY_STRING);
    }

    @Test
    public void testBlocking_objectToObject_nullRequestBody() {
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cannot serialize null value");
    }

    @Test
    public void testAsync_objectToObject_expectedCase() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .body(RESPONSE_STRING)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        assertThat(asyncClient.objectToObject(HEADER, PATH, QUERY, BODY).get()).isEqualTo(RESPONSE);
    }

    @Test
    public void testBlocking_objectToObject_throwsWhenResponseBodyIsEmpty() {
        server.enqueue(new MockResponse.Builder()
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, BODY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize response stream");
    }

    @Test
    public void testBlocking_voidToVoid_doesNotThrowWhenResponseBodyIsNonEmpty() {
        server.enqueue(new MockResponse.Builder().body("Unexpected response").build());
        blockingClient.voidToVoid();
    }

    @Test
    public void testAsync_voidToVoid_doesNotThrowWhenResponseBodyIsNonEmpty() throws Exception {
        server.enqueue(new MockResponse.Builder().body("Unexpected response").build());
        assertThat(asyncClient.voidToVoid().get()).isNull();
    }

    @Test
    public void testAsync_objectToObject_throwsWhenResponseBodyIsEmpty() {
        server.enqueue(new MockResponse.Builder()
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
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
        server.enqueue(new MockResponse.Builder().build());
        blockingClient.voidToVoid();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getUrl().pathSegments()).containsExactly("voidToVoid");
    }

    @Test
    public void testAsync_voidToVoid_expectedCase() throws Exception {
        server.enqueue(new MockResponse.Builder().build());
        assertThat(asyncClient.voidToVoid().get()).isNull();
    }

    @SuppressWarnings("for-rollout:deprecation")
    @Test
    @Timeout(2)
    public void testBlocking_throwsOnConnectError() throws Exception {
        server.close();
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, BODY))
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(ConnectException.class)
                .hasMessageMatching(".*((Connection refused)|(Failed to connect)).*");
    }

    @Test // see client construction: we set a 1s timeout
    @Timeout(5)
    public void testBlocking_throwsOnTimeout() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .body("\"response\"")
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyDelay(10, TimeUnit.SECONDS)
                .build());
        assertThatThrownBy(() -> blockingClient.objectToObject(HEADER, PATH, QUERY, BODY))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @Timeout(2)
    public void testAsync_throwsOnConnectError() throws Exception {
        server.close();
        assertThatThrownBy(() -> asyncClient.voidToVoid().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ConnectException.class)
                .hasMessageMatching(".*((Connection refused)|(Failed to connect)).*");
    }
}
