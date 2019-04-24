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

import com.palantir.conjure.java.dialogue.serde.DefaultErrorDecoder;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class HttpChannelTest extends AbstractChannelTest {

    @Override
    Channel createChannel(URL baseUrl, ExecutorService executor) {
        HttpClient client = HttpClient.newBuilder().build();
        ErrorDecoder errorDecoder = DefaultErrorDecoder.INSTANCE;
        return HttpChannel.of(client, executor, baseUrl, errorDecoder);
    }
}
