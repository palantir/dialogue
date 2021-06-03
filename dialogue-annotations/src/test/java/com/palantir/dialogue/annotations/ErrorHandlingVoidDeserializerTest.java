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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class ErrorHandlingVoidDeserializerTest {

    @Mock
    private ErrorDecoder errorDecoder;

    @Mock
    private Deserializer<Void> delegateDeserializer;

    @Mock
    private Response response;

    private ErrorHandlingVoidDeserializer errorHandlingVoidDeserializer;

    @BeforeEach
    public void beforeEach() {
        errorHandlingVoidDeserializer = new ErrorHandlingVoidDeserializer(delegateDeserializer, errorDecoder);
    }

    @AfterEach
    public void afterEach() {
        verifyNoMoreInteractions(errorDecoder, delegateDeserializer, response);
    }

    @Test
    public void testOnErrorCallsErrorDecoder() {
        RuntimeException runtimeException = new RuntimeException();
        when(errorDecoder.isError(response)).thenReturn(true);
        when(errorDecoder.decode(response)).thenReturn(runtimeException);

        assertThatThrownBy(() -> errorHandlingVoidDeserializer.deserialize(response))
                .isEqualTo(runtimeException);
        verify(response).close();
    }

    @Test
    public void testOnSuccessCallsDeserializer() {
        when(errorDecoder.isError(response)).thenReturn(false);

        errorHandlingVoidDeserializer.deserialize(response);

        verify(delegateDeserializer).deserialize(response);
        verify(response).close();
    }
}
