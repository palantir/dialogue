/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.hash.Hashing;
import com.palantir.logsafe.DoNotLog;
import com.palantir.logsafe.Preconditions;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** An ordered, typed composite key. Selector names and endpoint names do not affect its encoding. */
@DoNotLog
public final class RoutingKey {
    /** Local metadata used by Dialogue node selection; never serialized onto the wire. */
    public static final RequestAttachmentKey<RoutingKey> ATTACHMENT_KEY = RequestAttachmentKey.create(RoutingKey.class);

    private final long hash;

    private RoutingKey(long hash) {
        this.hash = hash;
    }

    public static RoutingKey of(Value... values) {
        return new RoutingKey(
                Hashing.murmur3_128().hashBytes(encode(List.of(values))).asLong());
    }

    /** The first 64 bits of Murmur3-128 over the versioned composite key encoding. */
    public long hash() {
        return hash;
    }

    @Override
    public String toString() {
        return "RoutingKey{redacted}";
    }

    /**
     * Version 1: big-endian int version, int component count, then int type, int UTF-8 byte length, and bytes for
     * each component. These explicit type identifiers and framing are independent of Java object hash codes.
     */
    private static byte[] encode(List<Value> values) {
        List<byte[]> encoded = values.stream()
                .map(value -> value.value().getBytes(StandardCharsets.UTF_8))
                .toList();
        int size = 8;
        for (byte[] value : encoded) {
            size = Math.addExact(size, Math.addExact(8, value.length));
        }
        ByteBuffer result = ByteBuffer.allocate(size).putInt(1).putInt(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.putInt(values.get(index).type().id)
                    .putInt(encoded.get(index).length)
                    .put(encoded.get(index));
        }
        return result.array();
    }

    public enum Type {
        ABSENT(0),
        STRING(1),
        INTEGER(2),
        DOUBLE(3),
        BOOLEAN(4),
        RID(5),
        UUID(6),
        DATETIME(7),
        ENUM(8),
        SAFELONG(9),
        BINARY(10),
        BEARERTOKEN(11);

        private final int id;

        Type(int id) {
            this.id = id;
        }
    }

    @DoNotLog
    public record Value(Type type, String value) {
        public Value {
            Preconditions.checkNotNull(type, "type");
            Preconditions.checkNotNull(value, "value");
            Preconditions.checkArgument(type != Type.ABSENT || value.isEmpty(), "Absent routing values must be empty");
        }

        public static Value absent() {
            return new Value(Type.ABSENT, "");
        }

        /** Encodes the remaining bytes as padded standard Base64 without modifying the buffer. */
        public static Value binary(ByteBuffer value) {
            ByteBuffer copy = value.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return new Value(Type.BINARY, Base64.getEncoder().encodeToString(bytes));
        }

        @Override
        public String toString() {
            return "RoutingValue{redacted}";
        }
    }
}
