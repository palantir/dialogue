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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class StickyAttachmentsTest {

    @Mock
    private LimitedChannel delegate;

    private LimitedChannel stickyTokenHandler = new LimitedChannel() {
        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
            return StickyAttachments.maybeAddStickyToken(delegate, endpoint, request, limitEnforcement);
        }
    };

    private SettableFuture<Response> responseSettableFuture = SettableFuture.create();

    @Test
    public void validate_is_passthrough_if_no_request_sticky_token_attachment() {
        Request request = Request.builder().build();
        when(delegate.maybeExecute(TestEndpoint.GET, request, LimitEnforcement.DEFAULT_ENABLED))
                .thenReturn(Optional.of(responseSettableFuture));
        assertThat(StickyAttachments.maybeExecuteAndValidateRequestStickyToken(
                        stickyTokenHandler, TestEndpoint.GET, request, LimitEnforcement.DEFAULT_ENABLED))
                .hasValue(responseSettableFuture);
    }

    @Test
    public void validate_is_passthrough_if_attachments_present() throws ExecutionException {
        Request request = Request.builder().build();
        StickyAttachments.requestStickyToken(request);
        when(delegate.maybeExecute(TestEndpoint.GET, request, LimitEnforcement.DEFAULT_ENABLED))
                .thenReturn(Optional.of(responseSettableFuture));
        ListenableFuture<Response> responseListenableFuture =
                StickyAttachments.maybeExecuteAndValidateRequestStickyToken(
                                stickyTokenHandler, TestEndpoint.GET, request, LimitEnforcement.DEFAULT_ENABLED)
                        .get();
        TestResponse response = TestResponse.withBody(null);
        responseSettableFuture.set(response);
        assertThat(Futures.getDone(responseListenableFuture)).isEqualTo(response);
    }

    @Test
    public void validate_throws_and_closes_response_if_attachments_are_not_present() {
        Request request = Request.builder().build();
        StickyAttachments.requestStickyToken(request);
        when(delegate.maybeExecute(TestEndpoint.GET, request, LimitEnforcement.DEFAULT_ENABLED))
                .thenReturn(Optional.of(responseSettableFuture));
        ListenableFuture<Response> responseListenableFuture =
                StickyAttachments.maybeExecuteAndValidateRequestStickyToken(
                                delegate, TestEndpoint.GET, request, LimitEnforcement.DEFAULT_ENABLED)
                        .get();
        TestResponse response = TestResponse.withBody(null);
        responseSettableFuture.set(response);

        assertThatThrownBy(() -> Futures.getDone(responseListenableFuture))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseExactlyInstanceOf(SafeRuntimeException.class)
                .hasRootCauseMessage("Requested sticky token on request but token not present on response");
        assertThat(response.isClosed()).isTrue();
    }
}
