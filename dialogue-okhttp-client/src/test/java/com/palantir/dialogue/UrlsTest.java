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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public final class UrlsTest {

    @Test
    public void pathSyntax() {
        Urls.create("https", "localhost", 1234, ""); // empty is OK
        Urls.create("https", "localhost", 1234, "/foo"); //  starts with / is OK

        // must start with /
        assertThatThrownBy(() -> Urls.create("https", "localhost", 1234, "foo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
