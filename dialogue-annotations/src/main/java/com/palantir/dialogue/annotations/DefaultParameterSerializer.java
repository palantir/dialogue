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

package com.palantir.dialogue.annotations;

import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.logsafe.DoNotLog;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.UUID;

public enum DefaultParameterSerializer implements ParameterSerializer {
    INSTANCE;

    @Override
    public String serializeBoolean(boolean in) {
        return in ? "true" : "false";
    }

    @Override
    public String serializeDouble(double in) {
        return Double.toString(in);
    }

    @Override
    public String serializeFloat(float in) {
        return Float.toString(in);
    }

    @Override
    public String serializeInteger(int in) {
        return Integer.toString(in);
    }

    @Override
    public String serializeLong(long in) {
        return Long.toString(in);
    }

    @Override
    public String serializeChar(char in) {
        return Character.toString(in);
    }

    @DoNotLog
    @Override
    public String serializeBearerToken(BearerToken in) {
        return in.toString();
    }

    @DoNotLog
    @Override
    public String serializeAuthHeader(AuthHeader in) {
        return in.toString();
    }

    @Override
    public String serializeDateTime(OffsetDateTime in) {
        return in.toString();
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
