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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.hash.Hashing;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

final class RoutingKeyTest {
    @Test
    void encodingIsVersionedFramedAndTyped() {
        byte[] vector = ByteBuffer.allocate(17)
                .putInt(1)
                .putInt(1)
                .putInt(1)
                .putInt(1)
                .put((byte) 'a')
                .array();
        assertThat(key("a").hash())
                .isEqualTo(Hashing.murmur3_128().hashBytes(vector).asLong());
        assertThat(key("ab", "c").hash()).isNotEqualTo(key("a", "bc").hash());
        assertThat(key("a", "b").hash()).isNotEqualTo(key("b", "a").hash());
        assertThat(key("").hash())
                .isNotEqualTo(RoutingKey.of(RoutingKey.Value.absent()).hash());
        assertThat(key("42").hash())
                .isNotEqualTo(RoutingKey.of(new RoutingKey.Value(RoutingKey.Type.INTEGER, "42"))
                        .hash());
    }

    @Test
    void binaryUsesRemainingBytesWithoutModifyingTheBuffer() {
        ByteBuffer data = ByteBuffer.allocateDirect(6);
        data.put(new byte[] {1, 0, (byte) 0xff, (byte) 0x80, 0x41, 2});
        data.position(1).limit(5).mark();
        ByteBuffer readOnly = data.asReadOnlyBuffer();
        assertThat(RoutingKey.Value.binary(readOnly))
                .isEqualTo(new RoutingKey.Value(RoutingKey.Type.BINARY, "AP+AQQ=="));
        assertThat(readOnly.position()).isEqualTo(1);
        assertThat(readOnly.limit()).isEqualTo(5);
        readOnly.reset();
        assertThat(data.position()).isEqualTo(1);
    }

    @Test
    void credentialsAndHashesAreRedacted() {
        RoutingKey.Value token = new RoutingKey.Value(RoutingKey.Type.BEARERTOKEN, "secret");
        assertThat(token.toString()).doesNotContain("secret");
        assertThat(RoutingKey.of(token).toString()).isEqualTo("RoutingKey{redacted}");
        assertThat(RoutingKey.of(token).hash()).isNotEqualTo(key("secret").hash());
    }

    @Test
    void attachmentCopiesAreLocalAndDoNotModifyOriginalRequests() {
        RequestAttachmentKey<String> other = RequestAttachmentKey.create(String.class);
        Request original = Request.builder()
                .putHeaderParams("example", "value")
                .putAttachment(other, "original")
                .build();
        Request routed = Request.builder()
                .from(original)
                .putAttachment(RoutingKey.ATTACHMENT_KEY, key("a"))
                .putAttachment(other, "copy")
                .build();
        assertThat(original.attachments().getOrDefault(RoutingKey.ATTACHMENT_KEY, null))
                .isNull();
        assertThat(original.attachments().getOrDefault(other, null)).isEqualTo("original");
        assertThat(routed.headerParams()).isEqualTo(original.headerParams());
        assertThat(routed.pathParameters().isEmpty()).isTrue();
        assertThat(routed.queryParams().isEmpty()).isTrue();
        assertThat(Request.builder().from(routed).build().attachments().getOrDefault(RoutingKey.ATTACHMENT_KEY, null))
                .isNotNull();
    }

    private static RoutingKey key(String... components) {
        return RoutingKey.of(java.util.Arrays.stream(components)
                .map(value -> new RoutingKey.Value(RoutingKey.Type.STRING, value))
                .toArray(RoutingKey.Value[]::new));
    }
}
