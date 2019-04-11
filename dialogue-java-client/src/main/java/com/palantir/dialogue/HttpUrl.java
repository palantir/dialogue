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

import com.palantir.logsafe.Preconditions;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A simplistic URL builder, not tuned for performance. */
public final class HttpUrl {
    private final URL url;

    public URL toUrl() {
        return url;
    }

    private HttpUrl(Builder builder) throws MalformedURLException {
        Preconditions.checkNotNull(builder.protocol, "protocol must be set");
        Preconditions.checkNotNull(builder.host, "host must be set");
        Preconditions.checkArgument(builder.port != -1, "port must be set");

        String path = encodePath(builder.pathSegments);
        String query = encodeQuery(builder.queryNamesAndValues);
        String file = path + (query.isEmpty() ? "" : ("?" + query));
        file = path.isEmpty() ? file : "/" + file;

        this.url = new URL(builder.protocol, builder.host, builder.port, file);
    }

    private static String encode(String string) {
        return URLEncoder.encode(string, StandardCharsets.UTF_8);
    }

    private static String encodePath(List<String> pairs) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pairs.size(); i += 1) {
            result.append(encode(pairs.get(i)));
            if (i < pairs.size() - 1) {
                result.append('/');
            }
        }
        return result.toString();
    }

    private static String encodeQuery(List<String> pairs) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pairs.size(); i += 2) {
            result.append(encode(pairs.get(i)));
            result.append('=');
            result.append(encode(pairs.get(i + 1)));
            if (i < pairs.size() - 2) {
                result.append('&');
            }
        }
        return result.toString();
    }

    public static Builder http() {
        return new Builder("http");
    }

    public static Builder https() {
        return new Builder("https");
    }

    static class Builder {
        private final String protocol;
        private String host;
        private int port = -1;
        private List<String> pathSegments = new ArrayList<>();
        private List<String> queryNamesAndValues = new ArrayList<>(); // alternating (name, value) pairs

        Builder(String protocol) {
            this.protocol = protocol;
        }

        public Builder host(String theHost) {
            this.host = theHost;
            return this;
        }

        public Builder port(int thePort) {
            this.port = thePort;
            return this;
        }

        public Builder pathSegment(String thePath) {
            this.pathSegments.add(thePath);
            return this;
        }

        public Builder queryParam(String name, String value) {
            this.queryNamesAndValues.add(name);
            this.queryNamesAndValues.add(value);
            return this;
        }

        public HttpUrl build() {
            try {
                return new HttpUrl(this);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Malformed URL", e);
            }
        }
    }
}
