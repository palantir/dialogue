/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations;

import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Response;
import java.io.InputStream;

public final class InputStreamDeserializer extends StdDeserializer<InputStream> {

    private final BodySerDe bodySerDe;

    public InputStreamDeserializer() {
        this(BodySerDeSingleton.DEFAULT_BODY_SERDE);
    }

    public InputStreamDeserializer(BodySerDe bodySerDe) {
        super(bodySerDe.inputStreamDeserializer().accepts().orElse("application/octet-stream"));
        this.bodySerDe = bodySerDe;
    }

    @Override
    public InputStream deserialize(Response response) {
        return bodySerDe.inputStreamDeserializer().deserialize(response);
    }

    @Override
    public String toString() {
        return "InputStreamDeserializer{}";
    }
}
