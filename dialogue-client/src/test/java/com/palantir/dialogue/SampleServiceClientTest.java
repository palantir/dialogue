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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.api.Observer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class SampleServiceClientTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Mock
    private Observer<String> stringObserver;
    @Mock
    private Observer<Void> voidObserver;

    private URL baseUrl;
    private SampleService blockingClient;
    private AsyncSampleService asyncClient;

    @Before
    public void before() {
        baseUrl = Urls.http("localhost", server.getPort());
        Channel channel = createChannel(baseUrl, Duration.ofSeconds(1));
        blockingClient = SampleServiceClient.blocking(channel);
        asyncClient = SampleServiceClient.async(channel);
    }

    private OkHttpChannel createChannel(URL url, Duration timeout) {
        return createChannel(url, timeout, MoreExecutors.newDirectExecutorService());
    }

    private OkHttpChannel createChannel(URL url, Duration timeout, ExecutorService executor) {
        return OkHttpChannel.of(new OkHttpClient.Builder()
                        .protocols(ImmutableList.of(Protocol.HTTP_1_1))
                        // Execute calls on same thread so that async tests are deterministic.
                        .dispatcher(new Dispatcher(executor))
                        .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                        .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                        .build(),
                url);
    }

    @Test
    public void testBlocking_stringToString_expectedCase() throws Exception {
        server.enqueue(new MockResponse().setBody("\"myResponse\""));
        assertThat(blockingClient.stringToString("myObject", "myHeader", "myBody")).isEqualTo("myResponse");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/stringToString/objects/myObject");
        assertThat(request.getHeader("headerKey")).isEqualTo("myHeader");
        assertThat(request.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("\"myBody\"");
    }

    @Test
    public void testBlocking_stringToString_nullRequestBody() throws Exception {
        assertThatThrownBy(() -> blockingClient.stringToString("myObject", "myHeader", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("body parameter must not be null");
    }

    @Test
    public void testAsync_stringToString_expectedCase() throws Exception {
        server.enqueue(new MockResponse().setBody("\"myResponse\""));
        asyncClient.stringToString("myObject", "myHeader", "myBody").execute(stringObserver);
        verify(stringObserver).success("myResponse");
        verifyNoMoreInteractions(stringObserver);
    }

    @Test
    public void testBlocking_stringToString_throwsWhenResponseBodyIsEmpty() throws Exception {
        server.enqueue(new MockResponse());
        assertThatThrownBy(() -> blockingClient.stringToString("myObject", "myHeader", "myBody"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to deserialize response stream to class java.lang.String in method stringToString");
    }

    @Test
    public void testAsync_stringToString_throwsWhenResponseBodyIsEmpty() throws Exception {
        server.enqueue(new MockResponse());
        asyncClient.stringToString("myObject", "myHeader", "myBody").execute(stringObserver);
        verifyObservesException(stringObserver, RuntimeException.class,
                "Failed to deserialize response stream to class java.lang.String in method stringToString");
        verifyNoMoreInteractions(stringObserver);
    }

    @Test
    public void testAsync_stringToString_nullRequestBody() throws Exception {
        assertThatThrownBy(() -> asyncClient.stringToString("myObject", "myHeader", null).execute(stringObserver))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("body parameter must not be null");
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
        asyncClient.voidToVoid().execute(voidObserver);
        verify(voidObserver).success(null);
        verifyNoMoreInteractions(voidObserver);
    }

    @Test
    public void testBlocking_voidToVoid_throwsWhenResponseBodyIsNonEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("Unexpected response"));
        assertThatThrownBy(() -> blockingClient.voidToVoid())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("voidToVoid expects null or empty response stream");
    }

    @Test
    public void testAsync_voidToVoid_throwsWhenResponseBodyIsEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("Unexpected response"));
        asyncClient.voidToVoid().execute(voidObserver);
        verifyObservesException(voidObserver, RuntimeException.class,
                "voidToVoid expects null or empty response stream");
        verifyNoMoreInteractions(voidObserver);
    }

    @Test
    public void testBlocking_throwsOnConnectError() throws Exception {
        blockingClient = SampleServiceClient.blocking(createChannel(baseUrl, Duration.ofSeconds(0)));
        server.shutdown();
        assertThatThrownBy(() -> blockingClient.stringToString("", "", ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("java.net.ConnectException: Failed to connect to localhost");
    }

    @Test(timeout = 60_000)
    public void testBlocking_throwsOnTimeout() throws Exception {
        blockingClient = SampleServiceClient.blocking(createChannel(baseUrl, Duration.ofMillis(500)));
        server.enqueue(new MockResponse().setBody("\"response\"").setBodyDelay(60, TimeUnit.SECONDS));
        assertThatThrownBy(() -> blockingClient.stringToString("", "", ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to deserialize response stream to class java.lang.String in method stringToString");
    }

    @Test
    public void testAsync_throwsOnConnectError() throws Exception {
        asyncClient = SampleServiceClient.async(createChannel(baseUrl, Duration.ofSeconds(0)));
        server.shutdown();

        asyncClient.voidToVoid().execute(voidObserver);
        verifyObservesException(voidObserver, ConnectException.class, "Failed to connect to localhost");
        verifyZeroInteractions(voidObserver);
    }

    @Test
    public void testAsync_throwsOnTimeout() throws Exception {
        asyncClient = SampleServiceClient.async(createChannel(baseUrl, Duration.ofMillis(500)));
        server.enqueue(new MockResponse().setBody("\"response\"").setBodyDelay(60, TimeUnit.SECONDS));

        asyncClient.voidToVoid().execute(voidObserver);
        verifyObservesException(voidObserver, RuntimeException.class,
                "Failed to consume response stream in method voidToVoid");
        verifyNoMoreInteractions(voidObserver);
    }

    @Test
    public void testAsync_canCancelCalls() throws Exception {
        asyncClient = SampleServiceClient.async(
                createChannel(baseUrl, Duration.ofSeconds(1), Executors.newSingleThreadExecutor()));

        server.enqueue(new MockResponse().setBody("\"response\"").setBodyDelay(60, TimeUnit.SECONDS));
        Call<Void> voidCall = asyncClient.voidToVoid();
        voidCall.execute(voidObserver);
        voidCall.cancel();

        server.enqueue(new MockResponse().setBody("\"response\"").setBodyDelay(60, TimeUnit.SECONDS));
        Call<String> stringCall = asyncClient.stringToString("", "", "");
        stringCall.execute(stringObserver);
        stringCall.cancel();

        Thread.sleep(100); // let cancellation propagate to observers
        verifyObservesException(voidObserver, IOException.class, "Canceled");
        verifyNoMoreInteractions(voidObserver);
        verifyObservesException(stringObserver, IOException.class, "Canceled");
        verifyNoMoreInteractions(stringObserver);
    }

    private static void verifyObservesException(Observer<?> observer, Class<?> clazz, String message) {
        ArgumentCaptor<Throwable> throwable = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).exception(throwable.capture());
        assertThat(throwable.getValue())
                .isInstanceOf(clazz)
                .hasMessageStartingWith(message);
    }
}
