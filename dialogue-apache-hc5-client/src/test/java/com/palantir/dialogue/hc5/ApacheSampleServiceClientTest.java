/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.hc5;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.AbstractSampleServiceClientTest;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import java.net.URL;
import java.time.Duration;

public final class ApacheSampleServiceClientTest extends AbstractSampleServiceClientTest {
    private static final ConjureRuntime runtime =
            DefaultConjureRuntime.builder().build();

    @Override
    protected SampleServiceBlocking createBlockingClient(URL baseUrl, Duration timeout) {
        return SampleServiceBlocking.of(channel(baseUrl, timeout), runtime);
    }

    @Override
    protected SampleServiceAsync createAsyncClient(URL baseUrl, Duration timeout) {
        return SampleServiceAsync.of(channel(baseUrl, timeout), runtime);
    }

    private static Channel channel(URL baseUrl, Duration timeout) {
        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create(baseUrl.toString()))
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
        return ApacheHttpClientChannels.create(config, "test-client");
    }
}
