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

import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.example.AsyncSampleService;
import com.palantir.dialogue.example.SampleService;
import com.palantir.dialogue.example.SampleServiceClient;
import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.SSLParameters;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class HttpSampleServiceClientTest extends AbstractSampleServiceClientTest {

    private static final ConjureRuntime runtime =
            DefaultConjureRuntime.builder().build();

    @Override
    SampleService createBlockingClient(URL baseUrl, Duration timeout) {
        Channel channel = createChannel(baseUrl, timeout);
        return SampleServiceClient.blocking(channel, runtime);
    }

    @Override
    AsyncSampleService createAsyncClient(URL baseUrl, Duration timeout) {
        Channel channel = createChannel(baseUrl, timeout);
        return SampleServiceClient.async(channel, runtime);
    }

    private HttpChannel createChannel(URL url, Duration timeout) {
        SSLParameters sslConfig = new SSLParameters(ALL_CIPHER_SUITES, new String[] {"TLSv1.2"});
        return HttpChannel.of(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .sslParameters(sslConfig)
                        .sslContext(SslSocketFactories.createSslContext(SSL_CONFIG))
                        .build(),
                url,
                timeout);
    }
}
