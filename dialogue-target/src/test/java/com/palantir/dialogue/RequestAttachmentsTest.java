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

import com.palantir.logsafe.exceptions.SafeNullPointerException;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;

public final class RequestAttachmentsTest {

    private static final RequestAttachmentKey<Long> KEY1 = RequestAttachmentKey.create(Long.class);
    private static final RequestAttachmentKey<Long> KEY2 = RequestAttachmentKey.create(Long.class);
    private final RequestAttachments attachments = RequestAttachments.create();

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

        attachments.put(KEY1, 3L);
        attachments.put(KEY2, 4L);

        assertThatKey(KEY1).isEqualTo(3L);
        assertThatKey(KEY2).isEqualTo(4L);
    }

    @Test
    public void testCannotPutNullKey() {
        RequestAttachmentKey<Long> key = null;
        assertThatLoggableExceptionThrownBy(() -> attachments.put(key, 1L))
                .isExactlyInstanceOf(SafeNullPointerException.class)
                .hasExactlyArgs()
                .hasMessage("key");
    }

    @Test
    public void testCannotPutNullValue() {
        assertThatLoggableExceptionThrownBy(() -> attachments.put(KEY1, null))
                .isExactlyInstanceOf(SafeNullPointerException.class)
                .hasExactlyArgs()
                .hasMessage("value");
    }

    @Test
    @SuppressWarnings("RawTypes")
    public void testCannotYolo() {
        assertThatThrownBy(() -> attachments.put((RequestAttachmentKey) KEY1, ""))
                .isExactlyInstanceOf(AssertionError.class)
                .hasMessage("Value not instance of class " + Long.class);
    }

    private AbstractLongAssert<?> assertThatKey(RequestAttachmentKey<Long> key) {
        return assertThat(attachments.getOrDefault(key, null));
    }
}
