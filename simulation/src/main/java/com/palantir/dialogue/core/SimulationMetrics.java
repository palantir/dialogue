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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.io.BufferedWriter;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a combination metric registry, reporter, logger and renderer, all hooked up to
 * {@link Simulation#clock()}.
 */
final class SimulationMetrics {
    private static final Logger log = LoggerFactory.getLogger(SimulationMetrics.class);

    private final Simulation simulation;
    private Map<String, Metric> metrics = new HashMap<>();

    // each of these is a named column
    private final LoadingCache<String, ArrayList<Double>> measurements =
            Caffeine.newBuilder().build(name -> new ArrayList<>());

    private static final String X_AXIS = "time_sec";

    SimulationMetrics(Simulation simulation) {
        this.simulation = simulation;
    }

    public Meter meter(String name) {
        if (!metrics.containsKey(name)) {
            Meter freshMeter = new Meter(simulation.codahaleClock());
            metrics.put(name, freshMeter);
            return freshMeter;
        } else {
            // have to support 'get existing' because multiple servers inside a composite might be named 'node1'
            Metric metric = metrics.get(name);
            Preconditions.checkState(
                    metric instanceof Meter, "Existing metric wasn't a meter", SafeArg.of("name", name));
            return (Meter) metric;
        }
    }

    public Counter counter(String name) {
        if (!metrics.containsKey(name)) {
            Counter fresh = new Counter();
            metrics.put(name, fresh);
            return fresh;
        } else {
            Metric metric = metrics.get(name);
            Preconditions.checkState(
                    metric instanceof Counter, "Existing metric wasn't a Counter", SafeArg.of("name", name));
            return (Counter) metric;
        }
    }

    @CheckReturnValue
    public Runnable startReporting(Duration interval) {
        metrics = ImmutableMap.copyOf(metrics); // just to make sure nobody tries to create any more after we start!
        AtomicBoolean keepRunning = new AtomicBoolean(true);
        reportInfinitely(keepRunning, interval);
        return () -> keepRunning.set(false);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void reportInfinitely(AtomicBoolean keepRunning, Duration interval) {
        long nanos = simulation.clock().read();
        double seconds = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) / 1000d;

        measurements.get(X_AXIS).add(seconds);
        metrics.forEach((name, metric) -> {

            if (metric instanceof Meter) {
                measurements.get(name + ".meter.1m").add(((Meter) metric).getOneMinuteRate());
                measurements.get(name + ".meter.count").add((double) ((Meter) metric).getCount());
            } else if (metric instanceof Counter) {
                measurements.get(name + ".counter.count").add((double) ((Counter) metric).getCount());
            } else {
                log.error("Unknown metric type {} {}", name, metric);
            }

        });

        if (keepRunning.get()) {
            simulation.schedule(
                    () -> reportInfinitely(keepRunning, interval), interval.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void dumpCsv(Path file) {
        ConcurrentMap<String, ArrayList<Double>> map = measurements.asMap();
        List<Double> xAxis = map.get(X_AXIS);
        List<String> columns = ImmutableList.copyOf(Sets.difference(map.keySet(), ImmutableSet.of(X_AXIS)));

        try (BufferedWriter writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            writer.write(X_AXIS);
            for (String column : columns) {
                writer.write(',');
                writer.write(column);
            }
            writer.newLine();

            for (int i = 0; i < xAxis.size(); i++) {
                writer.write(Double.toString(xAxis.get(i)));
                for (String column : columns) {
                    writer.write(',');
                    writer.write(Double.toString(map.get(column).get(i)));
                }
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void dumpPng(Path file) {
        dumpPng(file, Pattern.compile(".*\\.counter\\.count").asPredicate());
    }

    public void dumpPng(Path file, Predicate<String> predicate) {
        XYChart chart = new XYChartBuilder()
                .width(800)
                .height(600)
                // .title(getClass().getSimpleName())
                .xAxisTitle(X_AXIS)
                // .yAxisTitle("Y axis")
                .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(5);
        chart.getStyler().setYAxisLabelAlignment(Styler.TextAlignment.Right);
        chart.getStyler().setPlotMargin(0);
        chart.getStyler().setPlotContentSize(.95);

        Map<String, ArrayList<Double>> map = measurements.asMap();
        double[] xAxis = map.get(X_AXIS).stream().mapToDouble(d -> d).toArray();
        List<String> columns = map.keySet().stream()
                .filter(name -> !name.equals(X_AXIS))
                .filter(predicate)
                .collect(Collectors.toList());

        for (String column : columns) {
            double[] series = map.get(column).stream().mapToDouble(d -> d).toArray();
            chart.addSeries(column, xAxis, series);
        }

        emitChart(file, chart);
    }

    private static void emitChart(Path file, XYChart chart) {
        Stopwatch sw = Stopwatch.createStarted();
        try {
            BitmapEncoder.saveBitmap(chart, file.toString(), BitmapEncoder.BitmapFormat.PNG);
            log.info("Generated {} ({} ms)", file, sw.elapsed(TimeUnit.MILLISECONDS));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
