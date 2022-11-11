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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.logsafe.DoNotLog;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Package private internal API. */
enum ConjurePlainSerDe implements PlainSerDe {
    INSTANCE;

    @DoNotLog
    @Override
    public String serializeBearerToken(BearerToken in) {
        return in.toString();
    }

    @Override
    public String serializeBoolean(boolean in) {
        return in ? "true" : "false";
    }

    @Override
    public String serializeDateTime(OffsetDateTime in) {
        return in.toString();
    }

    @Override
    public String serializeDouble(double in) {
        return Double.toString(in);
    }

    @Override
    public String serializeInteger(int in) {
        return Integer.toString(in);
    }

    @Override
    public String serializeRid(ResourceIdentifier in) {
        return in.toString();
    }

    @Override
    public String serializeSafeLong(SafeLong in) {
        return in.toString();
    }

    @Override
    public String serializeString(String in) {
        return in;
    }

    @Override
    public String serializeUuid(UUID in) {
        return in.toString();
    }
}
