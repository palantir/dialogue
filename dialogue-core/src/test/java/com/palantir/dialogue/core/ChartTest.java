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

import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;

public class ChartTest {

    @Test
    public void render_pls() throws IOException {
        XYChart chart = getChart(
                new double[] {
                    60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84,
                    85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100
                },
                new double[] {
                    800000, 835286, 873456, 927048, 969305, 1030749, 1101102, 1171396, 1246486, 1329076, 1424666,
                            1424173,
                    1421853, 1397093, 1381882, 1364562, 1360050, 1336885, 1340431, 1312217, 1288274, 1271615, 1262682,
                            1237287,
                    1211335, 1191953, 1159689, 1117412, 1078875, 1021020, 974933, 910189, 869154, 798476, 744934,
                            674501,
                    609237, 524516, 442234, 343960, 257025
                },
                new double[] {
                    800000, 791439, 809744, 837020, 871166, 914836, 958257, 1002955, 1054094, 1118934, 1194071, 1185041,
                    1175401, 1156578, 1132121, 1094879, 1066202, 1054411, 1028619, 987730, 944977, 914929, 880687,
                    809330, 783318, 739751, 696201, 638242, 565197, 496959, 421280, 358113, 276518, 195571, 109514,
                    13876, 29, 0, 0, 0, 0
                });
        String fileName = Paths.get("build/chart").toAbsolutePath().toString();
        BitmapEncoder.saveBitmap(chart, fileName + ".png", BitmapEncoder.BitmapFormat.PNG);

        System.out.println(fileName);
    }

    private XYChart getChart(double[] xAxis, double[] series1, double[] series2) {

        // Create Chart
        XYChart chart = new XYChartBuilder()
                .width(800)
                .height(600)
                .title(getClass().getSimpleName())
                .xAxisTitle("This is the X axis")
                .yAxisTitle("This is the Y axis")
                .build();

        // Customize Chart
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setYAxisLabelAlignment(Styler.TextAlignment.Right);
        chart.getStyler().setPlotMargin(0);
        chart.getStyler().setPlotContentSize(.95);

        chart.addSeries("series1", xAxis, series1);
        chart.addSeries("series2", xAxis, series2);

        return chart;
    }
}
