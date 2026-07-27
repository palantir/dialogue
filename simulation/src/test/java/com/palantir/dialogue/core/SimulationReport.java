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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregates every {@code .txt} summary and {@code .png} chart under {@code src/test/resources} into a single
 * {@code report.md} so GitHub renders the diffs. Invoked from the {@code @AfterAll} of each simulation test class
 * (e.g. {@link SimulationTest}, {@link LoadSweepTest}) rather than a single class, so the report is rebuilt after
 * whichever class runs last and captures its fresh output regardless of test-class ordering.
 */
final class SimulationReport {

    static void regenerate() throws IOException {
        // squish all txt files together into one markdown report so that github displays diffs
        String txtSection = buildTxtSection();
        String images = buildImagesTable();
        String report = String.format(
                "# Report%n<!-- Run SimulationTest and LoadSweepTest to regenerate this report. -->%n%s%n%n%s%n",
                txtSection, images);
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

    private SimulationReport() {}
}
