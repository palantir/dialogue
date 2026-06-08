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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInfo;
import org.knowm.xchart.XYChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the golden-file artifacts that {@link SimulationTest} checks in: a per-test {@code .txt} summary, a
 * {@code .png} chart, and a combined {@code report.md}. This code was moved verbatim out of {@code SimulationTest}.
 */
final class SimulationReport {
    private static final Logger log = LoggerFactory.getLogger(SimulationReport.class);

    private final Simulation simulation;

    SimulationReport(Simulation simulation) {
        this.simulation = simulation;
    }

    void record(TestInfo testInfo, Strategy strategy, Benchmark.BenchmarkResult result) throws IOException {
        Stopwatch after = Stopwatch.createStarted();
        Duration serverCpu = Duration.ofNanos(
                MetricNames.globalServerTimeNanos(simulation.taggedMetrics()).getCount());
        long clientMeanNanos = (long) result.clientHistogram().getMean();
        double clientMeanMillis = TimeUnit.NANOSECONDS.toMillis(clientMeanNanos);

        // intentionally using tabs so that opening report.txt with 'cat' aligns columns nicely
        StringBuilder longSummaryBuilder = new StringBuilder();
        longSummaryBuilder.append(String.format(
                "success=%s%%\tclient_mean=%-15s\tserver_cpu=%-15s\tclient_received=%s/%s\tserver_resps=%s\tcodes=%s\n",
                result.successPercentage(),
                Duration.of(clientMeanNanos, ChronoUnit.NANOS),
                serverCpu,
                result.numReceived(),
                result.numSent(),
                result.numGlobalResponses(),
                result.statusCodes()));
        result.perEndpointHistograms()
                .forEach((name, snapshot) -> longSummaryBuilder.append(String.format(
                        "client=%s\tclient_mean=%-15s\n",
                        name, Duration.of((long) snapshot.getMean(), ChronoUnit.NANOS))));

        String longSummary = longSummaryBuilder.toString();

        String methodName = testInfo.getTestMethod().get().getName() + "[" + strategy + "]";

        Path txt = Paths.get("src/test/resources/txt/" + methodName + ".txt");
        String pngPath = "src/test/resources/" + methodName + ".png";
        String onDisk = Files.exists(txt) ? new String(Files.readAllBytes(txt), StandardCharsets.UTF_8) : "";

        boolean txtChanged = !longSummary.equals(onDisk);

        if (System.getenv().containsKey("CI")) { // only strict on CI, locally we just overwrite
            assertThat(onDisk)
                    .describedAs("Run tests locally to update checked-in file: %s", txt)
                    .isEqualTo(longSummary);
            assertThat(Paths.get(pngPath)).exists();
        } else if (txtChanged || !Files.exists(Paths.get(pngPath))) {
            // only re-generate PNGs if the txt file changed (as they're slow af)
            List<XYChart> charts = new ArrayList<>();
            Stopwatch sw = Stopwatch.createStarted();
            Files.write(
                    txt,
                    longSummary.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            XYChart activeRequestsPerServerNode =
                    simulation.metricsReporter().chart(MetricNames.serverActiveRequestsPattern());
            activeRequestsPerServerNode.setTitle(String.format(
                    "%s success=%s%% client_mean=%.1f ms server_cpu=%s",
                    strategy, result.successPercentage(), clientMeanMillis, serverCpu));
            charts.add(activeRequestsPerServerNode);

            // Github UIs don't let you easily diff pngs that are stored in git lfs. We just keep around the .prev.png
            // on disk to aid local iteration.
            if (Files.exists(Paths.get(pngPath))) {
                Path previousPng = Paths.get(pngPath.replaceAll("\\.png", "\\.prev.png"));
                Files.deleteIfExists(previousPng);
                Files.move(Paths.get(pngPath), previousPng);
            }

            charts.add(simulation.metricsReporter().chart(MetricNames.serverRequestMeterPattern()));

            charts.addAll(simulation.metricsReporter().charts(MetricNames.perClientEndpointResponseTimerPattern()));

            // charts.add(simulation.metrics().chart(Pattern.compile("(responseClose|globalResponses)")));

            SimulationMetricsReporter.png(pngPath, charts);
            log.info("Generated {} ({} ms)", pngPath, sw.elapsed(TimeUnit.MILLISECONDS));
        }

        log.warn("after() ({} ms)", after.elapsed(TimeUnit.MILLISECONDS));
    }

    static void writeMarkdownReport() throws IOException {
        // squish all txt files together into one markdown report so that github displays diffs
        String txtSection = buildTxtSection();
        String images = buildImagesTable();
        String report = String.format(
                "# Report%n<!-- Run SimulationTest to regenerate this report. -->%n%s%n%n%s%n", txtSection, images);
        Files.write(Paths.get("src/test/resources/report.md"), report.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildTxtSection() throws IOException {
        try (Stream<Path> list = Files.list(Paths.get("src/test/resources/txt"))) {
            List<Path> files = list.filter(p -> !p.toString().endsWith("report.md"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            return files.stream()
                    .filter(p -> p.toString().endsWith("txt"))
                    .map(p -> {
                        try {
                            return String.format(
                                    "%70s:\t%s%n",
                                    p.getFileName().toString(),
                                    new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .collect(Collectors.joining("", "```\n", "```\n"));
        }
    }

    private static String buildImagesTable() throws IOException {
        try (Stream<Path> files = Files.list(Paths.get("src/test/resources"))) {
            return files.filter(
                            p -> p.toString().endsWith("png") && !p.toString().endsWith(".prev.png"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(p -> {
                        String githubLfsUrl = "https://media.githubusercontent.com/media/palantir/dialogue/develop/"
                                + "simulation/src/test/resources/"
                                + p.getFileName();
                        return String.format(
                                "%n## `%s`%n"
                                        + "<table><tr><th>develop</th><th>current</th></tr>%n"
                                        + "<tr>"
                                        + "<td><image width=400 src=\"%s\" /></td>"
                                        + "<td><image width=400 src=\"%s\" /></td>"
                                        + "</tr>"
                                        + "</table>%n%n",
                                p.getFileName().toString().replaceAll("\\.png", ""), githubLfsUrl, p.getFileName());
                    })
                    .collect(Collectors.joining());
        }
    }
}
