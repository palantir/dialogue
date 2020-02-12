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

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SimulationMetrics {

    private final SimulatedScheduler simulation;
    private Map<String, Meter> meters = new HashMap<>();

    // each of these is a named column
    private final LoadingCache<String, ArrayList<Double>> measurements =
            Caffeine.newBuilder().build(name -> new ArrayList<>());

    private static final String X_AXIS = "time_sec";

    SimulationMetrics(SimulatedScheduler simulation) {
        this.simulation = simulation;
    }

    public Meter meter(String name) {
        Meter meter = new Meter(simulation.codahaleClock());
        Meter existing = meters.put(name, meter);
        Preconditions.checkState(existing == null);
        return meter;
    }

    @CheckReturnValue
    public Closeable startReporting(Duration interval) {
        meters = ImmutableMap.copyOf(meters); // just to make sure nobody tries to create any more after we start!
        AtomicBoolean keepRunning = new AtomicBoolean();
        reportInfinitely(keepRunning, interval);
        return () -> keepRunning.set(false);
    }

    public void dumpCsv(Path file) throws IOException {
        Files.createDirectories(file.getParent());

        ConcurrentMap<String, ArrayList<Double>> map = measurements.asMap();
        ArrayList<Double> xAxis = map.get(X_AXIS);
        List<String> columns = ImmutableList.copyOf(Sets.difference(map.keySet(), ImmutableSet.of(X_AXIS)));

        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            writer.write(X_AXIS);
            for (String column : columns) {
                writer.write(',');
                writer.write(column);
                writer.newLine();
            }

            for (int i = 0; i < xAxis.size(); i++) {
                writer.write(Double.toString(xAxis.get(i)));
                for (String column : columns) {
                    writer.write(',');
                    writer.write(Double.toString(map.get(column).get(i)));
                }
                writer.newLine();
            }
        }
    }

    private void reportInfinitely(AtomicBoolean keepRunning, Duration interval) {
        long nanos = simulation.clock().read();
        double seconds = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) / 1000d;

        measurements.get(X_AXIS).add(seconds);
        meters.forEach((name, metric) -> {
            measurements.get(name).add(metric.getOneMinuteRate());
        });

        if (keepRunning.get()) {
            simulation.schedule(
                    () -> reportInfinitely(keepRunning, interval),
                    interval.toNanos(),
                    TimeUnit.NANOSECONDS);
        }
    }
}
