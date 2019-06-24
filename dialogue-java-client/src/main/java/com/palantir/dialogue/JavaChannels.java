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

import static java.util.stream.Collectors.toList;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.core.Channels;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public final class JavaChannels {

    private JavaChannels() {}

    public static Channel create(ClientConfiguration conf, UserAgent baseAgent, TaggedMetricRegistry metrics) {
        // TODO(jellis): read/write timeouts
        // TODO(jellis): gcm cipher toggle
        // TODO(jellis): proxy creds + mesh proxy
        // TODO(jellis): configure node selection strategy
        // TODO(jellis): failed url cooldown
        // TODO(jellis): backoff slot size (possibly unnecessary)
        // TODO(jellis): client QoS, server QoS, retries?

        HttpClient client = HttpClient.newBuilder()
                .executor(createExecutor())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(conf.connectTimeout())
                .proxy(conf.proxy())
                .sslContext(createSslContext(conf.trustManager()))
                .build();

        List<Channel> channels = conf.uris().stream()
                .map(uri -> HttpChannel.of(client, url(uri)))
                .collect(toList());

        return Channels.create(channels, baseAgent, metrics);
    }

    private static URL url(String uri) {
        try {
            return new URL(uri);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static SSLContext createSslContext(TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[] {}, new TrustManager[] {trustManager}, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // Same default thread pool with tracing enabled
    private static ExecutorService createExecutor() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("dialogue-%d")
                .setDaemon(true)
                .build();
        return Tracers.wrap("dialogue-execute", Executors.newCachedThreadPool(threadFactory));
    }
}
