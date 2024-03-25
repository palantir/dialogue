/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.testing.Assertions;
import com.palantir.logsafe.testing.LoggableExceptionAssert;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.internal.Failures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class RefreshingChannelFactoryTest {
    private static final String SERVICE_A = "serviceA";
    private static final String URI_1 = "uri-1";
    private static final ServicesConfigBlock SERVICE_A_CONFIG_1 = ServicesConfigBlock.builder()
            .putServices(
                    SERVICE_A,
                    PartialServiceConfiguration.builder()
                            .addUris(URI_1)
                            .security(TestConfigurations.SSL_CONFIG)
                            .build())
            .build();
    private static final String URI_2 = "uri-2";
    private static final ServicesConfigBlock SERVICE_A_CONFIG_2 = ServicesConfigBlock.builder()
            .putServices(
                    SERVICE_A,
                    PartialServiceConfiguration.builder()
                            .addUris(URI_2)
                            .security(TestConfigurations.SSL_CONFIG)
                            .build())
            .build();

    @Mock
    private RefreshingChannelFactory.ChannelFactory channelFactory;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private Channel channel1;

    @Mock
    private Channel channel2;

    private AtomicReference<ServicesConfigBlock> conf = new AtomicReference<>(ServicesConfigBlock.empty());
    private RefreshingChannelFactory clientFactory;
    private Channel channelA;

    @BeforeEach
    public void before() {
        lenient().when(channelFactory.create(matchesConf(URI_1))).thenReturn(channel1);
        lenient().when(channelFactory.create(matchesConf(URI_2))).thenReturn(channel2);
        lenient().when(channel1.execute(endpoint, request)).thenReturn(Futures.immediateFuture(response));
        lenient().when(channel2.execute(endpoint, request)).thenReturn(Futures.immediateFuture(response));

        clientFactory = new RefreshingChannelFactory(() -> conf.get(), channelFactory);
        channelA = clientFactory.create(SERVICE_A);
    }

    @Test
    public void testServiceNotConfigured() {
        assertThatLoggableException(channelA.execute(endpoint, request))
                .hasLogMessage("Service not configured")
                .hasExactlyArgs(SafeArg.of("serviceName", SERVICE_A));
    }

    @Test
    public void testServiceConfigured() throws ExecutionException, InterruptedException {
        conf.set(SERVICE_A_CONFIG_1);

        channelA.execute(endpoint, request).get();
        channelA.execute(endpoint, request).get();

        verify(channel1, times(2)).execute(endpoint, request);
        verify(channelFactory).create(matchesConf(URI_1));
        verifyNoMoreInteractions(channelFactory);
    }

    @Test
    public void testConfiguredServiceChanges() throws ExecutionException, InterruptedException {
        conf.set(SERVICE_A_CONFIG_1);
        channelA.execute(endpoint, request).get();

        conf.set(SERVICE_A_CONFIG_2);
        channelA.execute(endpoint, request).get();

        verify(channel1).execute(endpoint, request);
        verify(channel2).execute(endpoint, request);

        verify(channelFactory).create(matchesConf(URI_1));
        verify(channelFactory).create(matchesConf(URI_2));

        verifyNoMoreInteractions(channelFactory);
    }

    // ClientConfiguration contains an SSLSocketFactory and X509TrustManager which rely on object equality
    // which is why we have our own matchers here
    public ClientConfiguration matchesConf(String uri) {
        return argThat(argument -> argument != null && argument.uris().contains(uri));
    }

    // TODO(jellis): move this into logsafe testing
    @SuppressWarnings("unchecked")
    public static <T extends Throwable & SafeLoggable> LoggableExceptionAssert<T> assertThatLoggableException(
            ListenableFuture<?> shouldRaiseThrowable) {
        try {
            shouldRaiseThrowable.get();
            return fail("Expected SafeLoggable exception");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (!SafeLoggable.class.isInstance(e.getCause())) {
                throw Failures.instance()
                        .failure(String.format(
                                "Expecting code to throw a SafeLoggable exception, " + "but caught a %s which does not",
                                e.getCause().getClass().getCanonicalName()));
            }

            return Assertions.assertThatLoggableException((T) e.getCause());
        }
    }
}
