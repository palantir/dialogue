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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.guava.api.Assertions.assertThat;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import java.util.Optional;
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

    @Test
    void from_method_copies_query_params_no_mutation() {
        Request request1 = Request.builder().putQueryParams("a", "A").build();
        Request request2 = Request.builder().from(request1).build();
        assertThat(request2.queryParams())
                .describedAs("Re-using the exact same underlying instance is a safe optimization because Requests are"
                        + " immutable")
                .isSameAs(request1.queryParams());
    }

    @Test
    void from_method_copies_query_params_with_mutation() {
        Request request1 = Request.builder()
                .putQueryParams("a", "A")
                .putQueryParams("b", "B1")
                .build();
        Request request2 = Request.builder()
                .from(request1)
                .putQueryParams("b", "B2")
                .putQueryParams("c", "C")
                .build();
        assertThat(request2.queryParams())
                .isEqualTo(ImmutableListMultimap.<String, String>builder()
                        .put("a", "A")
                        .putAll("b", "B1", "B2")
                        .put("c", "C")
                        .build());
    }

    @Test
    void from_method_copies_path_params_no_mutation() {
        Request request1 = Request.builder().putPathParams("a", "A").build();
        Request request2 = Request.builder().from(request1).build();
        assertThat(request2.pathParameters())
                .describedAs("Re-using the exact same underlying instance is a safe optimization because Requests are"
                        + " immutable")
                .isSameAs(request1.pathParameters());
    }

    @Test
    void from_method_copies_path_params_with_mutation() {
        Request request1 = Request.builder()
                .putPathParams("a", "A")
                .putPathParams("b", "B1")
                .build();
        Request request2 = Request.builder()
                .from(request1)
                .putPathParams("b", "B2")
                .putPathParams("c", "C")
                .build();
        assertThat(request2.pathParameters())
                .isEqualTo(ImmutableListMultimap.<String, String>builder()
                        .put("a", "A")
                        .putAll("b", "B1", "B2")
                        .put("c", "C")
                        .build());
    }

    @Test
    void from_method_replaces_previously_mutated_params() {
        Request existing = Request.builder()
                .putHeaderParams("header", "existing")
                .putQueryParams("query", "existing")
                .putPathParams("path", "existing")
                .build();
        Request request = Request.builder()
                .putHeaderParams("header", "discarded")
                .putQueryParams("query", "discarded")
                .putPathParams("path", "discarded")
                .from(existing)
                .putHeaderParams("header", "added")
                .putQueryParams("query", "added")
                .putPathParams("path", "added")
                .build();
        assertThat(request.headerParams()).isEqualTo(ImmutableListMultimap.of("header", "existing", "header", "added"));
        assertThat(request.queryParams()).isEqualTo(ImmutableListMultimap.of("query", "existing", "query", "added"));
        assertThat(request.pathParameters()).isEqualTo(ImmutableListMultimap.of("path", "existing", "path", "added"));
    }

    @Test
    void request_body_rejects_null() {
        assertThatThrownBy(() ->
                        Request.builder().body((Optional<RequestBody>) null).build())
                .isExactlyInstanceOf(SafeNullPointerException.class);
    }

    @Test
    void request_body_accepts_empty() {
        Request request1 = Request.builder().body(Optional.empty()).build();
        assertThat(request1.body()).isEmpty();
    }
}
