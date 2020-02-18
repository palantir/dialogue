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

package com.palantir.dialogue.core;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Wrapper around {@link ThreadLocalRandom} which can be used as a drop in replacement for <code>new Random()</code>.
 * Unlike {@link ThreadLocalRandom}, an instance of {@link SafeThreadLocalRandom} may be used by multiple threads
 * because each method invocation delegates to {@link ThreadLocalRandom#current()}.
 *
 * Note that because this is a shared instance, {@link Random#setSeed(long)} is unsupported.
 * This is <b>not</b> a {@link java.security.SecureRandom} instance and must not be used for cryptography.
 */
final class SafeThreadLocalRandom extends Random {

    private static final Random instance = new SafeThreadLocalRandom();
    private boolean initialized;

    private SafeThreadLocalRandom() {
        super(0L);
        initialized = true;
    }

    /** Gets the singleton {@link SafeThreadLocalRandom} instance. */
    static Random get() {
        return instance;
    }

    @Override
    public void setSeed(long seed) {
        // setSeed is invoked once in the constructor
        if (initialized) {
            throw new UnsupportedOperationException("SafeThreadLocalRandom does not support setSeed");
        }
    }

    @Override
    protected int next(int bits) {
        throw new UnsupportedOperationException(
                "Internal Random#next(int) is not supported, this should only be thrown if an override is missing");
    }

    @Override
    public void nextBytes(byte[] bytes) {
        ThreadLocalRandom.current().nextBytes(bytes);
    }

    @Override
    public int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    @Override
    public long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    @Override
    public float nextFloat() {
        return ThreadLocalRandom.current().nextFloat();
    }

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    @Override
    public double nextGaussian() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    @Override
    public IntStream ints(long streamSize) {
        return ThreadLocalRandom.current().ints(streamSize);
    }

    @Override
    public IntStream ints() {
        return ThreadLocalRandom.current().ints();
    }

    @Override
    public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        return ThreadLocalRandom.current().ints(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        return ThreadLocalRandom.current().ints(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public LongStream longs(long streamSize) {
        return ThreadLocalRandom.current().longs(streamSize);
    }

    @Override
    public LongStream longs() {
        return ThreadLocalRandom.current().longs();
    }

    @Override
    public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        return ThreadLocalRandom.current().longs(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        return ThreadLocalRandom.current().longs(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public DoubleStream doubles(long streamSize) {
        return ThreadLocalRandom.current().doubles(streamSize);
    }

    @Override
    public DoubleStream doubles() {
        return ThreadLocalRandom.current().doubles();
    }

    @Override
    public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
        return ThreadLocalRandom.current().doubles(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        return ThreadLocalRandom.current().doubles(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public String toString() {
        return "SafeThreadLocalRandom{}";
    }
}
