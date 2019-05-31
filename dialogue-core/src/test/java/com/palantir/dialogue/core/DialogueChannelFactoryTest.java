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

package com.palantir.dialogue.core;

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.logsafe.SafeArg;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DialogueChannelFactoryTest {
    private static final String SERVICE_A = "serviceA";
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"),
            Paths.get("src/test/resources/keyStore.jks"),
            "keystore");
    private static final ServicesConfigBlock EMPTY_CONFIG = ServicesConfigBlock.builder().build();
    private static final String URI_1 = "uri-1";
    private static final ServicesConfigBlock SERVICE_A_CONFIG_1 = ServicesConfigBlock.builder()
            .putServices(SERVICE_A, PartialServiceConfiguration.builder()
                    .addUris(URI_1)
                    .security(SSL_CONFIG)
                    .build())
            .build();
    private static final String URI_2 = "uri-2";
    private static final ServicesConfigBlock SERVICE_A_CONFIG_2 = ServicesConfigBlock.builder()
            .putServices(SERVICE_A, PartialServiceConfiguration.builder()
                    .addUris(URI_2)
                    .security(SSL_CONFIG)
                    .build())
            .build();


    @Mock private DialogueChannelFactory.ChannelFactory channelFactory;
    @Mock private Endpoint endpoint;
    @Mock private Request request;
    @Mock private Channel channel1;
    @Mock private Channel channel2;
    private AtomicReference<ServicesConfigBlock> conf = new AtomicReference<>(EMPTY_CONFIG);
    private DialogueChannelFactory clientFactory;
    private Channel channelA;

    @Before
    public void before() {
        when(channelFactory.create(matchesConf1())).thenReturn(channel1);
        when(channelFactory.create(matchesConf2())).thenReturn(channel2);

        clientFactory = new DialogueChannelFactory(() -> conf.get(), channelFactory);
        channelA = clientFactory.create(SERVICE_A);
    }

    @Test
    public void testServiceNotConfigured() {
        assertThatLoggableExceptionThrownBy(() -> channelA.execute(endpoint, request))
                .hasLogMessage("Service not configured")
                .hasExactlyArgs(SafeArg.of("serviceName", SERVICE_A));

        verify(channelFactory, never()).create(any());
    }

    @Test
    public void testServiceConfigured() {
        conf.set(SERVICE_A_CONFIG_1);

        channelA.execute(endpoint, request);
        channelA.execute(endpoint, request);

        verify(channel1, times(2)).execute(endpoint, request);
        verify(channelFactory).create(matchesConf1());
        verifyNoMoreInteractions(channelFactory);
    }

    @Test
    public void testConfiguredServiceChanges() {
        conf.set(SERVICE_A_CONFIG_1);
        channelA.execute(endpoint, request);

        conf.set(SERVICE_A_CONFIG_2);
        channelA.execute(endpoint, request);

        verify(channel1).execute(endpoint, request);
        verify(channel2).execute(endpoint, request);

        verify(channelFactory).create(matchesConf1());
        verify(channelFactory).create(matchesConf2());

        verifyNoMoreInteractions(channelFactory);
    }

    // ClientConfiguration contains an SSLSocketFactory and X509TrustManager which rely on object equality
    // which is why we have our own matchers here
    public ClientConfiguration matchesConf1() {
        return argThat(argument -> argument != null && argument.uris().contains(URI_1));
    }

    public ClientConfiguration matchesConf2() {
        return argThat(argument -> argument != null && argument.uris().contains(URI_2));
    }
}
