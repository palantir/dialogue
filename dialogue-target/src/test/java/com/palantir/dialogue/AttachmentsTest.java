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

package com.palantir.dialogue;

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.dialogue.Attachments.AttachmentKey;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;

public final class AttachmentsTest {

    private static final AttachmentKey<Long> KEY1 = Attachments.createAttachmentKey(Long.class);
    private static final AttachmentKey<Long> KEY2 = Attachments.createAttachmentKey(Long.class);
    private final Attachments attachments = Attachments.create();

    @Test
    public void testInitiallyEmpty() {
        assertThat(attachments.getOrDefault(KEY1, null)).isNull();
        assertThat(attachments.getOrDefault(KEY2, null)).isNull();
    }

    @Test
    public void testCanPutValues() {
        attachments.put(KEY1, 1L);
        attachments.put(KEY2, 2L);

        assertThatKey(KEY1).isEqualTo(1L);
        assertThatKey(KEY2).isEqualTo(2L);
    }

    @Test
    public void testCanOverwriteValues() {
        attachments.put(KEY1, 1L);
        attachments.put(KEY2, 2L);

        assertThat(attachments.put(KEY1, 3L)).isEqualTo(1L);
        assertThat(attachments.put(KEY2, 4L)).isEqualTo(2L);

        assertThatKey(KEY1).isEqualTo(3L);
        assertThatKey(KEY2).isEqualTo(4L);
    }

    @Test
    public void testCannotPutNullKey() {
        AttachmentKey<Long> key = null;
        assertThatLoggableExceptionThrownBy(() -> attachments.put(key, 1L))
                .isExactlyInstanceOf(SafeNullPointerException.class)
                .hasNoArgs()
                .hasMessage("key");
    }

    @Test
    public void testCannotPutNullValue() {
        assertThatLoggableExceptionThrownBy(() -> attachments.put(KEY1, null))
                .isExactlyInstanceOf(SafeNullPointerException.class)
                .hasNoArgs()
                .hasMessage("value");
    }

    @Test
    @SuppressWarnings("RawTypes")
    public void testCannotAttachIncorrectType() {
        assertThatThrownBy(() -> attachments.put((AttachmentKey) KEY1, ""))
                .isExactlyInstanceOf(SafeIllegalArgumentException.class)
                .hasMessageContaining("Unexpected type");
    }

    private AbstractLongAssert<?> assertThatKey(AttachmentKey<Long> key) {
        return assertThat(attachments.getOrDefault(key, null));
    }
}
