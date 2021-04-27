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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.palantir.dialogue.annotations.MultipartRequestBody.RequestBodyPartBuilder;
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
                        .value("{\"i-am-json\":\"indeed-i-am\"}")
                        .contentType(MediaType.JSON_UTF_8.toString())
                        .putKeyValues("If-Match", "version")
                        .build());

        MultipartBody okhttp = createOkhttpMultipartBody(keyValues);
        MultipartRequestBody dialogue = createDialogueMultipartRequestBody(keyValues);

        assertOkhttpAndDialogueMatch(okhttp, dialogue);
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
                .addFormBodyPart(
                        MultipartRequestBody.formBodyPartBuilder(name, new com.palantir.dialogue.RequestBody() {
                                    @Override
                                    public void writeTo(OutputStream output) throws IOException {
                                        Files.copy(filePath, output);
                                    }

                                    @Override
                                    public String contentType() {
                                        return contentType;
                                    }

                                    @Override
                                    public boolean repeatable() {
                                        return true;
                                    }

                                    @Override
                                    public void close() {
                                        // Noop
                                    }
                                })
                                .setFileName(fileName))
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
            builder.addRequestBodyPart(MultipartRequestBody.requestBodyPartBuilder(
                            byteArrayUnknownLengthRequestBody(mediaTypeString, entry.getValue()))
                    .addHeaderValue("Content-Disposition", "form-data; name=\"" + entry.getKey() + "\"")
                    .addHeaderValue("Content-Transfer-Encoding", contentTransferEncoding));
        }

        return builder.build();
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
                    .build());

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

            RequestBodyPartBuilder requestBodyPartBuilder = MultipartRequestBody.requestBodyPartBuilder(
                    byteArrayUnknownLengthRequestBody(entry.contentType(), value.getBytes(CHARSET)));
            requestBodyPartBuilder.addHeaderValue("bucket", bucket);
            requestBodyPartBuilder.addHeaderValue("key", key);
            entry.keyValues().forEach(requestBodyPartBuilder::addHeaderValue);
            builder.addRequestBodyPart(requestBodyPartBuilder);
        }

        return builder.build();
    }

    private com.palantir.dialogue.RequestBody byteArrayUnknownLengthRequestBody(String contentType, byte[] value) {
        return new com.palantir.dialogue.RequestBody() {
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
    @Value.Style(stagedBuilder = true)
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
