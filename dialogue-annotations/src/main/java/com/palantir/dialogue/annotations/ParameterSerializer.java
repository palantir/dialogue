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
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface ParameterSerializer {

    String serializeBearerToken(BearerToken in);

    String serializeAuthHeader(AuthHeader in);

    String serializeBoolean(boolean in);

    String serializeDateTime(OffsetDateTime in);

    String serializeDouble(double in);

    String serializeFloat(float in);

    String serializeInteger(int in);

    String serializeLong(long in);

    String serializeChar(char in);

    String serializeRid(ResourceIdentifier in);

    String serializeSafeLong(SafeLong in);

    String serializeString(String in);

    String serializeUuid(UUID in);
}
