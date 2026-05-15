/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.TestEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class CachingEndpointChannelFactoryTest {

    @Mock
    private EndpointChannelFactory delegate;

    @Mock
    private EndpointChannel channel1;

    @Mock
    private EndpointChannel channel2;

    @Test
    public void cache_hit_returns_same_instance() {
        when(delegate.endpoint(TestEndpoint.GET)).thenReturn(channel1);
        CachingEndpointChannelFactory factory = new CachingEndpointChannelFactory(delegate, 100);

        EndpointChannel first = factory.endpoint(TestEndpoint.GET);
        EndpointChannel second = factory.endpoint(TestEndpoint.GET);

        assertThat(first).isSameAs(second);
        verify(delegate, times(1)).endpoint(TestEndpoint.GET);
    }

    @Test
    public void different_endpoints_get_different_channels() {
        when(delegate.endpoint(TestEndpoint.GET)).thenReturn(channel1);
        when(delegate.endpoint(TestEndpoint.POST)).thenReturn(channel2);
        CachingEndpointChannelFactory factory = new CachingEndpointChannelFactory(delegate, 100);

        EndpointChannel get = factory.endpoint(TestEndpoint.GET);
        EndpointChannel post = factory.endpoint(TestEndpoint.POST);

        assertThat(get).isSameAs(channel1);
        assertThat(post).isSameAs(channel2);
        verify(delegate, times(1)).endpoint(TestEndpoint.GET);
        verify(delegate, times(1)).endpoint(TestEndpoint.POST);
    }

    @Test
    public void toString_includes_delegate() {
        EndpointChannelFactory namedDelegate = new EndpointChannelFactory() {
            @Override
            public EndpointChannel endpoint(com.palantir.dialogue.Endpoint _endpoint) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                return "TestDelegate";
            }
        };
        CachingEndpointChannelFactory factory = new CachingEndpointChannelFactory(namedDelegate, 100);
        assertThat(factory.toString()).contains("TestDelegate");
    }
}
