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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public final class RequestAttachmentsTest {

    private static final RequestAttachmentKey<Boolean> ATTACHMENT_KEY = RequestAttachmentKey.create(Boolean.class);

    @Test
    public void testSanity() {
        RequestAttachments requestAttachments = RequestAttachments.create();
        requestAttachments.put(ATTACHMENT_KEY, true);
        assertThat(requestAttachments.getOrDefault(ATTACHMENT_KEY, false)).isTrue();
    }
}
