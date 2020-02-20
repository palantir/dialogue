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

import com.palantir.dialogue.Channel;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.net.URI;

public class ClientPoolImpl implements ClientPool {
    @Override
    public <T> T get(Class<T> dialogueInterface, Listenable<ClientConfig> config) {
        return null;
    }

    @Override
    public Channel smartChannel(Listenable<ClientConfig> config) {
        return null;
    }

    @Override
    public Channel rawChannel(URI uri, Listenable<ClientConfig> config) {
        ClientConfig clientConfig = config.get(); // TODO(dfox): live reloading!

        // TODO(dfox): jokes we can't directly compile against any of the impls as this would be circular... SERVICELOAD
        switch (clientConfig.rawClientType) {
            case APACHE:
                // ApacheHttpClientChannels.create(clientConfig)
                break;
            case OKHTTP:
                break;
            case HTTP_URL_CONNECTION:
                break;
            case JAVA9_HTTPCLIENT:
                break;
        }

        throw new SafeIllegalArgumentException(
                "Unable to construct a raw channel", SafeArg.of("type", clientConfig.rawClientType));
    }

    @Override
    public void close() {}
}
