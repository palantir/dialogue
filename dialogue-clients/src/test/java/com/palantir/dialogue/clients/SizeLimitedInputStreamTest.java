/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests all the different InputStream read methods against various cases (e.g. reading fewer/more/exactly as many bytes
 * as the configured limit, as well as when the stream has less/more/equals as many bytes as the configured limit).
 */
public class SizeLimitedInputStreamTest {

    private static final int BYTES_LIMIT = 1024;

    enum BytesAvailable {
        BELOW_LIMIT(512),
        EXACT_LIMIT(1024),
        ABOVE_LIMIT(2048);

        private final int size;

        BytesAvailable(int size) {
            this.size = size;
        }
    }

    enum BytesRead {
        ZERO,
        BELOW_ALL,
        AT_MIN_AVAILABLE_AND_LIMIT,
        BETWEEN_AVAILABLE_AND_LIMIT,
        AT_MAX_AVAILABLE_AND_LIMIT,
        ABOVE_ALL;

        private int get(BytesAvailable bytes) {
            int min = Math.min(bytes.size, BYTES_LIMIT);
            int max = Math.max(bytes.size, BYTES_LIMIT);
            return switch (this) {
                case ZERO -> 0;
                case BELOW_ALL -> min / 2;
                case AT_MIN_AVAILABLE_AND_LIMIT -> min;
                case BETWEEN_AVAILABLE_AND_LIMIT -> min + (max - min) / 2;
                case AT_MAX_AVAILABLE_AND_LIMIT -> max;
                case ABOVE_ALL -> max * 2;
            };
        }
    }

    static Stream<Arguments> testCases() {
        return Stream.of(BytesAvailable.values())
                .flatMap(available -> Stream.of(BytesRead.values()).map(read -> Arguments.of(available, read)));
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void read(BytesAvailable available, BytesRead read) {
        test(s -> s.read() != -1 ? 1 : 0, available, read);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void readBuffer(BytesAvailable available, BytesRead read) {
        test(s -> s.read(new byte[64]), available, read);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void readBufferOffset(BytesAvailable available, BytesRead read) {
        test(s -> s.read(new byte[128], 32, 64), available, read);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void readNBytes(BytesAvailable available, BytesRead read) {
        test(s -> s.readNBytes(64).length, available, read);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void readNBytesBuffer(BytesAvailable available, BytesRead read) {
        test(s -> s.readNBytes(new byte[128], 32, 64), available, read);
    }

    @ParameterizedTest
    @EnumSource
    void readAllBytes(BytesAvailable available) {
        BytesRead read =
                switch (available) {
                    case BELOW_LIMIT, EXACT_LIMIT -> BytesRead.AT_MIN_AVAILABLE_AND_LIMIT;
                    case ABOVE_LIMIT -> BytesRead.AT_MAX_AVAILABLE_AND_LIMIT;
                };
        test(s -> s.readAllBytes().length, available, read);
    }

    interface Reader {
        int read(InputStream stream) throws IOException;
    }

    private static void test(Reader readBytes, BytesAvailable available, BytesRead read) {
        try (SizeLimitedInputStream stream = buildStream(available.size)) {
            int toRead = read.get(available);
            if (available.size > BYTES_LIMIT && toRead > BYTES_LIMIT) {
                assertThatException()
                        .isThrownBy(() -> readDesired(readBytes, stream, toRead))
                        .isInstanceOf(ResponseSizeTooLargeException.class);
            } else {
                assertThat(readDesired(readBytes, stream, toRead))
                        .isEqualTo(Math.min(Math.min(toRead, BYTES_LIMIT), available.size));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int readDesired(Reader readBytes, InputStream stream, int toRead) {
        int total = 0;
        while (total < toRead) {
            int count;
            try {
                count = readBytes.read(stream);
            } catch (IOException e) {
                throw new SafeUncheckedIoException(e);
            }
            if (count <= 0) {
                break;
            }
            total += count;
        }

        return total;
    }

    private static SizeLimitedInputStream buildStream(int readableBytes) {
        byte[] bytes = new byte[readableBytes];
        for (int i = 0; i < readableBytes; i++) {
            bytes[i] = (byte) (i % 256);
        }
        return new SizeLimitedInputStream(new ByteArrayInputStream(bytes), BYTES_LIMIT);
    }
}
