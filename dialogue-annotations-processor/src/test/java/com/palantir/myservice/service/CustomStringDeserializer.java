/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.myservice.service;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.annotations.StdDeserializer;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deserializes a string out of CSV like: {@code "mystring,<value>"}, cause why not.
 */
public final class CustomStringDeserializer extends StdDeserializer<String> {

    private static final Splitter SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    public CustomStringDeserializer() {
        super("text/csv");
    }

    @Override
    public String deserialize(Response response) {
        try (InputStream is = response.body()) {
            String csv = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
            List<String> fields = SPLITTER.splitToList(csv);
            Preconditions.checkState(fields.size() == 2);
            Preconditions.checkState("mystring".equals(fields.get(0)));
            Preconditions.checkState(!Strings.isNullOrEmpty(fields.get(1)));
            return fields.get(1);
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to serialize payload", e);
        }
    }
}
