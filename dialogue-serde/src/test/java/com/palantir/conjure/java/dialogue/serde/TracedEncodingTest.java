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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.io.ByteStreams;
import com.palantir.dialogue.TypeMarker;
import com.palantir.tracing.AlwaysSampler;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.Span;
import com.palantir.tracing.api.SpanObserver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TracedEncodingTest {

    @BeforeEach
    void before() {
        Tracer.setSampler(AlwaysSampler.INSTANCE);
        Tracer.getAndClearTrace();
    }

    @Test
    void testTypeString_string() {
        // non-generic types use the simple name
        assertThat(TracedEncoding.toString(new TypeMarker<String>() {})).isEqualTo("String");
    }

    @Test
    void testTypeString_stringList() {
        // It's more complicated to generate 'List<String>' from Type, that can be done later if necessary
        assertThat(TracedEncoding.toString(new TypeMarker<List<String>>() {}))
                .isEqualTo("java.util.List<java.lang.String>");
    }

    @Test
    void testSerializerOperationName() throws IOException {
        Encoding.Serializer<String> serializer =
                new TracedEncoding(new StubEncoding()).serializer(new TypeMarker<String>() {});
        SpanObserver mockObserver = mock(SpanObserver.class);
        Tracer.subscribe("test", mockObserver);
        try {
            serializer.serialize("value", ByteStreams.nullOutputStream());
        } finally {
            Tracer.unsubscribe("test");
        }
        ArgumentCaptor<Span> captor = ArgumentCaptor.forClass(Span.class);
        verify(mockObserver).consume(captor.capture());
        Span span = captor.getValue();
        assertThat(span.getOperation()).isEqualTo("Dialogue: serialize");
        assertThat(span.getMetadata()).containsEntry("type", "String").containsEntry("contentType", "application/stub");
    }

    @Test
    void testDeserializerOperationName() throws IOException {
        Encoding.Deserializer<String> deserializer =
                new TracedEncoding(new StubEncoding()).deserializer(new TypeMarker<String>() {});
        SpanObserver mockObserver = mock(SpanObserver.class);
        Tracer.subscribe("test", mockObserver);
        try {
            deserializer.deserialize(new ByteArrayInputStream(new byte[0]));
        } finally {
            Tracer.unsubscribe("test");
        }
        ArgumentCaptor<Span> captor = ArgumentCaptor.forClass(Span.class);
        verify(mockObserver).consume(captor.capture());
        Span span = captor.getValue();
        assertThat(span.getOperation()).isEqualTo("Dialogue: deserialize");
        assertThat(span.getMetadata()).containsEntry("type", "String").containsEntry("contentType", "application/stub");
    }

    private static final class StubEncoding implements Encoding {

        @Override
        public <T> Serializer<T> serializer(TypeMarker<T> _type) {
            return (_value, _output) -> {};
        }

        @Override
        public <T> Deserializer<T> deserializer(TypeMarker<T> _type) {
            return _input -> null;
        }

        @Override
        public String getContentType() {
            return "application/stub";
        }

        @Override
        public boolean supportsContentType(String _contentType) {
            return true;
        }
    }
}
