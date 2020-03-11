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
package com.palantir.dialogue.httpurlconnection;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.blocking.BlockingChannelAdapter;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.net.MalformedURLException;
import java.net.URL;

/** Simple Channel factory which produces channels based on HttpURLConnection. */
public final class HttpUrlConnectionChannels {

    private HttpUrlConnectionChannels() {}

    public static Channel create(ClientConfiguration conf) {
        return DialogueChannel.builder()
                .channelName("http-url-channel")
                .clientConfiguration(conf)
                .channelFactory(uri -> BlockingChannelAdapter.of(new HttpUrlConnectionBlockingChannel(conf, url(uri))))
                .build();
    }

    private static URL url(String uri) {
        try {
            return new URL(uri);
        } catch (MalformedURLException e) {
            throw new SafeIllegalArgumentException("Failed to parse URL", e);
        }
    }
}
