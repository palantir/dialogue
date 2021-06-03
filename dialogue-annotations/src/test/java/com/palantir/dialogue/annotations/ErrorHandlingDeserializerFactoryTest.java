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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TypeMarker;
import java.io.Closeable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class ErrorHandlingDeserializerFactoryTest {

    @Mock
    private ErrorDecoder errorDecoder;

    @Mock
    private DeserializerFactory<Object> delegateDeserializerFactory;

    @Mock
    private Deserializer<Integer> integerDelegateDeserializer;

    @Mock
    private Response response;

    private ErrorHandlingDeserializerFactory<Object> errorHandlingDeserializerFactory;
    private final TypeMarker<Integer> integerTypeMarker = new TypeMarker<>() {};

    @BeforeEach
    public void beforeEach() {
        errorHandlingDeserializerFactory =
                new ErrorHandlingDeserializerFactory<>(delegateDeserializerFactory, errorDecoder);
    }

    @AfterEach
    public void afterEach() {
        verifyNoMoreInteractions(integerDelegateDeserializer, delegateDeserializerFactory, errorDecoder, response);
    }

    @Test
    public void testOnErrorCallsErrorDecoder() {
        RuntimeException runtimeException = new RuntimeException();
        when(errorDecoder.isError(response)).thenReturn(true);
        when(errorDecoder.decode(response)).thenReturn(runtimeException);
        Deserializer<Integer> integerDeserializer = expectIntegerDeserializer();

        assertThatThrownBy(() -> integerDeserializer.deserialize(response)).isEqualTo(runtimeException);
        verify(response).close();
    }

    @Test
    public void testOnSuccessCallsDeserializer() {
        int toReturn = 1;
        when(errorDecoder.isError(response)).thenReturn(false);

        when(integerDelegateDeserializer.deserialize(response)).thenReturn(toReturn);

        Deserializer<Integer> integerDeserializer = expectIntegerDeserializer();
        assertThat(integerDeserializer.deserialize(response)).isEqualTo(toReturn);

        verify(response).close();
    }

    interface MyCloseableType extends Closeable {}

    @Test
    public void testOnSuccessDoesNotCloseIfTypeCloseable() {
        TypeMarker<MyCloseableType> myCloseableTypeTypeMarker = new TypeMarker<MyCloseableType>() {};
        MyCloseableType toReturn = mock(MyCloseableType.class);
        when(errorDecoder.isError(response)).thenReturn(false);
        Deserializer<MyCloseableType> myCloseableTypeDelegateDeserializer = mock(Deserializer.class);
        when(myCloseableTypeDelegateDeserializer.deserialize(response)).thenReturn(toReturn);

        Deserializer<MyCloseableType> myCloseableTypeDeserializer =
                expectDeserializer(myCloseableTypeTypeMarker, myCloseableTypeDelegateDeserializer);
        assertThat(myCloseableTypeDeserializer.deserialize(response)).isEqualTo(toReturn);

        verify(response, never()).close();
    }

    private Deserializer<Integer> expectIntegerDeserializer() {
        when(delegateDeserializerFactory.deserializerFor(integerTypeMarker)).thenReturn(integerDelegateDeserializer);
        return errorHandlingDeserializerFactory.deserializerFor(integerTypeMarker);
    }

    private <T> Deserializer<T> expectDeserializer(TypeMarker<T> typeMarker, Deserializer<T> delegateDeserializer) {
        when(delegateDeserializerFactory.deserializerFor(typeMarker)).thenReturn(delegateDeserializer);
        return errorHandlingDeserializerFactory.deserializerFor(typeMarker);
    }
}
