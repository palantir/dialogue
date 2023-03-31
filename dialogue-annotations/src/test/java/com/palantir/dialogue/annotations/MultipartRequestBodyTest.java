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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.palantir.dialogue.annotations.MultipartRequestBody.ContentBodyPartBuilder;
import com.palantir.dialogue.annotations.MultipartRequestBody.Part;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import okhttp3.Headers;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class MultipartRequestBodyTest {

    private static final String BOUNDARY = "xxxxxxxxxxxxxxxxxxxxxxxx";
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    public static final String I_AM_JSON_INDEED_I_AM = "{\"i-am-json\":\"indeed-i-am\"}";

    @Test
    public void testCanSupportClient1BinaryValueWithCustomHeaders() {
        List<KeyValue> keyValues = ImmutableList.of(
                ImmutableKeyValue.builder()
                        .key("key1")
                        .bucket("bucket")
                        .value("I am String: indeed I am")
                        .contentType(MediaType.PLAIN_TEXT_UTF_8.toString())
                        .build(),
                ImmutableKeyValue.builder()
                        .key("key2")
                        .bucket("bucket")
                        .value(I_AM_JSON_INDEED_I_AM)
                        .contentType(MediaType.JSON_UTF_8.toString())
                        .putKeyValues("If-Match", "version")
                        .build());

        MultipartBody okhttp = createOkhttpMultipartBody(keyValues);
        MultipartRequestBody dialogue = createDialogueMultipartRequestBody(keyValues);

        assertOkhttpAndDialogueMatch(okhttp, dialogue);
    }

    private MultipartBody createOkhttpMultipartBody(List<KeyValue> keyValues) {
        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder(BOUNDARY);
        multipartBodyBuilder.setType(MultipartBody.MIXED);

        for (KeyValue entry : keyValues) {
            final String bucket = entry.bucket();
            final String key = entry.key();
            final String value = entry.value();

            Headers headers = Headers.of(ImmutableMap.<String, String>builder()
                    .put("bucket", bucket)
                    .put("key", key)
                    .putAll(entry.keyValues())
                    .buildOrThrow());

            okhttp3.MediaType contentType = okhttp3.MediaType.parse(entry.contentType());
            RequestBody body = unknownLengthRequestBody(value.getBytes(CHARSET), contentType);

            multipartBodyBuilder.addPart(MultipartBody.Part.create(headers, body));
        }

        return multipartBodyBuilder.build();
    }

    private MultipartRequestBody createDialogueMultipartRequestBody(List<KeyValue> keyValues) {
        MultipartRequestBody.Builder builder = MultipartRequestBody.builder();
        builder.boundary(BOUNDARY);

        for (KeyValue entry : keyValues) {
            final String bucket = entry.bucket();
            final String key = entry.key();
            final String value = entry.value();

            ContentBodyPartBuilder contentBodyPartBuilder = MultipartRequestBody.contentBodyPartBuilder(
                    byteArrayUnknownLengthRequestBody(entry.contentType(), value.getBytes(CHARSET)));
            contentBodyPartBuilder.addHeaderValue("bucket", bucket);
            contentBodyPartBuilder.addHeaderValue("key", key);
            entry.keyValues().forEach(contentBodyPartBuilder::addHeaderValue);
            builder.addPart(contentBodyPartBuilder.build());
        }

        return builder.build();
    }

    @Test
    public void testCanSupportClient2Form(@TempDir Path tempDir) throws IOException {
        String name = "jarfile";
        String fileName = "job.jar";
        Path filePath = tempDir.resolve("job.jar");
        Files.write(filePath, "hello".getBytes(CHARSET));
        String contentType = "application/x-java-archive";
        MultipartBody okhttp = new MultipartBody.Builder(BOUNDARY)
                .addPart(MultipartBody.Part.createFormData(
                        name,
                        fileName,
                        unknownLengthRequestBody(Files.readAllBytes(filePath), okhttp3.MediaType.parse(contentType))))
                .build();

        MultipartRequestBody dialogue = MultipartRequestBody.builder()
                .boundary(BOUNDARY)
                .addPart(MultipartRequestBody.formBodyPartBuilder(name, ContentBody.path(contentType, filePath))
                        .fileName(fileName)
                        .build())
                .build();

        assertOkhttpAndDialogueMatch(okhttp, dialogue);
    }

    @Test
    public void testCanSupportClient3PartMap() {
        String fileName = "file.bin";
        String mediaType = "application/octet-stream";
        String contentTransferEncoding = "binary";
        Map<String, byte[]> partMap = new HashMap<>();
        byte[] file = "file".getBytes(CHARSET);
        partMap.put("attachment\"; filename=\"" + fileName, file);

        MultipartBody okhttp = createOkhttpPartMapBody(partMap, mediaType, contentTransferEncoding);

        MultipartRequestBody dialogue = createDialoguePartMapBody(partMap, mediaType, contentTransferEncoding);

        assertOkhttpAndDialogueMatch(okhttp, dialogue);
    }

    private MultipartBody createOkhttpPartMapBody(
            Map<String, byte[]> value, String mediaTypeString, String contentTransferEncoding) {

        MultipartBody.Builder builder = new MultipartBody.Builder(BOUNDARY);
        okhttp3.MediaType mediaType = okhttp3.MediaType.get(mediaTypeString);

        for (Map.Entry<String, byte[]> entry : value.entrySet()) {
            String entryKey = entry.getKey();
            RequestBody entryValue = unknownLengthRequestBody(entry.getValue(), mediaType);

            Headers headers = Headers.of(
                    "Content-Disposition",
                    "form-data; name=\"" + entryKey + "\"",
                    "Content-Transfer-Encoding",
                    contentTransferEncoding);

            builder.addPart(headers, entryValue);
        }

        return builder.build();
    }

    private MultipartRequestBody createDialoguePartMapBody(
            Map<String, byte[]> value, String mediaTypeString, String contentTransferEncoding) {

        MultipartRequestBody.Builder builder = MultipartRequestBody.builder().boundary(BOUNDARY);

        for (Map.Entry<String, byte[]> entry : value.entrySet()) {
            builder.addPart(MultipartRequestBody.contentBodyPartBuilder(
                            byteArrayUnknownLengthRequestBody(mediaTypeString, entry.getValue()))
                    .addHeaderValue("Content-Disposition", "form-data; name=\"" + entry.getKey() + "\"")
                    .addHeaderValue("Content-Transfer-Encoding", contentTransferEncoding)
                    .build());
        }

        return builder.build();
    }

    @Test
    public void testCanSupportClient3FormNullFilename() {
        byte[] httpMediaContent = "someMedia".getBytes(CHARSET);
        String httpMediaType = "text/plain";
        String uploadField = "upload";
        String httpMediaFileName = "filename";
        String detailsField = "details";
        byte[] detailsBytes = I_AM_JSON_INDEED_I_AM.getBytes(CHARSET);
        String detailsContentType = "application/json";

        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder(BOUNDARY);
        multipartBodyBuilder.setType(okhttp3.MediaType.parse("multipart/form-data"));
        RequestBody body = unknownLengthRequestBody(httpMediaContent, okhttp3.MediaType.parse(httpMediaType));
        multipartBodyBuilder.addPart(MultipartBody.Part.createFormData(uploadField, httpMediaFileName, body));
        RequestBody detailsBody = unknownLengthRequestBody(detailsBytes, okhttp3.MediaType.parse(detailsContentType));
        multipartBodyBuilder.addPart(MultipartBody.Part.createFormData(detailsField, null, detailsBody));
        MultipartBody okhttp = multipartBodyBuilder.build();

        MultipartRequestBody dialogue = MultipartRequestBody.builder()
                .boundary(BOUNDARY)
                .addPart(MultipartRequestBody.formBodyPartBuilder(
                                uploadField, byteArrayUnknownLengthRequestBody(httpMediaType, httpMediaContent))
                        .fileName(httpMediaFileName)
                        .build())
                .addPart(MultipartRequestBody.formBodyPartBuilder(
                                detailsField, byteArrayUnknownLengthRequestBody(detailsContentType, detailsBytes))
                        .build())
                .build();

        assertOkhttpAndDialogueMatch(okhttp, dialogue);
    }

    @Test
    public void testIsRepeatableIfAllPartsAreRepeatable(@TempDir Path tempDir) {
        Part nonRepeatablePart = MultipartRequestBody.formBodyPartBuilder(
                        "hello",
                        ContentBody.inputStream(
                                "application/json", new ByteArrayInputStream("hello".getBytes(CHARSET))))
                .build();
        Part repeatablePart = MultipartRequestBody.formBodyPartBuilder(
                        "hello", ContentBody.path("application/json", tempDir))
                .build();
        assertThat(MultipartRequestBody.builder()
                        .boundary(BOUNDARY)
                        .addPart(nonRepeatablePart)
                        .addPart(repeatablePart)
                        .build()
                        .repeatable())
                .isFalse();
        assertThat(MultipartRequestBody.builder()
                        .boundary(BOUNDARY)
                        .addPart(repeatablePart)
                        .addPart(repeatablePart)
                        .build()
                        .repeatable())
                .isTrue();
    }

    @Test
    public void testCloseClosesAllParts() throws IOException {
        ContentBody throwingBody = mock(ContentBody.class);
        when(throwingBody.contentType()).thenReturn("application/json");
        doThrow(new RuntimeException("I throw when you close"))
                .when(throwingBody)
                .close();
        ContentBody happyBody = mock(ContentBody.class);
        when(happyBody.contentType()).thenReturn("application/json");
        Part throwingPart =
                MultipartRequestBody.contentBodyPartBuilder(throwingBody).build();
        Part happyPart = MultipartRequestBody.contentBodyPartBuilder(happyBody).build();
        MultipartRequestBody multipartRequestBody = MultipartRequestBody.builder()
                .addPart(throwingPart)
                .addPart(happyPart)
                .build();

        multipartRequestBody.writeTo(new ByteArrayOutputStream());
        verify(throwingBody, never()).close();
        verify(happyBody, never()).close();

        assertThatCode(multipartRequestBody::close).doesNotThrowAnyException();
        verify(throwingBody).close();
        verify(happyBody).close();
    }

    private com.palantir.dialogue.annotations.ContentBody byteArrayUnknownLengthRequestBody(
            String contentType, byte[] value) {
        return new com.palantir.dialogue.annotations.ContentBody() {
            @Override
            public void writeTo(OutputStream output) throws IOException {
                output.write(value);
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public boolean repeatable() {
                return false;
            }

            @Override
            public void close() {}
        };
    }

    @Value.Immutable
    interface KeyValue {
        String key();

        String bucket();

        String value();

        Map<String, String> keyValues();

        String contentType();
    }

    private RequestBody unknownLengthRequestBody(byte[] value, okhttp3.MediaType contentType) {
        return new RequestBody() {
            @Nullable
            @Override
            public okhttp3.MediaType contentType() {
                return contentType;
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (Source source = Okio.source(new ByteArrayInputStream(value))) {
                    sink.writeAll(source);
                }
            }
        };
    }

    private void assertOkhttpAndDialogueMatch(MultipartBody okhttp, MultipartRequestBody dialogue) {
        try {
            Buffer buffer = new Buffer();
            okhttp.writeTo(buffer);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            dialogue.writeTo(byteArrayOutputStream);

            assertThat(byteArrayOutputStream.toString(CHARSET.name())).isEqualTo(buffer.readString(CHARSET));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
