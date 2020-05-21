/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.guava.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMultimap;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import org.junit.jupiter.api.Test;

public final class RequestTest {

    @Test
    public void testRequestHeaderInsensitivity() {
        Request request = Request.builder().putHeaderParams("Foo", "bar").build();
        assertThat(request.headerParams())
                .containsKeys("foo")
                .containsKeys("FOO")
                .containsKeys("Foo");
    }

    @Test
    public void testHeadersAreRedacted() {
        String sentinel = "shouldnotbelogged";
        BearerToken token = BearerToken.valueOf(sentinel);
        Request request = Request.builder()
                .putHeaderParams("authorization", AuthHeader.of(token).toString())
                .putHeaderParams("other", token.toString())
                .build();
        assertThat(request).asString().doesNotContain(sentinel);
    }

    @Test
    void from_method_copies_headers_no_mutation() {
        Request request1 =
                Request.builder().putHeaderParams("Authorization", "foo").build();
        Request request2 = Request.builder().from(request1).build();
        assertThat(request2.headerParams())
                .describedAs("Re-using the exact same underlying instance is a safe optimization because Requests are"
                        + " immutable")
                .isSameAs(request1.headerParams());
    }

    @Test
    void from_method_copies_headers_with_mutation() {
        Request request1 = Request.builder()
                .putHeaderParams("Authorization", "foo")
                .putHeaderParams("accept-encoding", "bar")
                .build();
        Request request2 = Request.builder()
                .from(request1)
                .putHeaderParams("accept-encoding", "baz") // TODO(dfox): I don't think users will like this behaviour
                .putHeaderParams("another-header", "another-value")
                .build();
        assertThat(request2.headerParams())
                .describedAs("We should preserve the Authorization header from request1, but we changed")
                .isEqualTo(ImmutableMultimap.<String, String>builder()
                        .put("Authorization", "foo")
                        .putAll("accept-encoding", "bar", "baz")
                        .putAll("another-header", "another-value")
                        .build());
    }
}
