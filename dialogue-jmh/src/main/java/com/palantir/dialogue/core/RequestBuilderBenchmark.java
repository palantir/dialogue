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

import com.palantir.dialogue.Request;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@Warmup(iterations = 12, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 12, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
public class RequestBuilderBenchmark {

    private Request empty = Request.builder().build();
    private Request nonEmpty = Request.builder()
            .putHeaderParams("Authorization", "whatever")
            .putHeaderParams("header1", "header")
            .putHeaderParams("header2", "header")
            .putHeaderParams("header3", "header")
            .putHeaderParams("header4", "header")
            .putPathParams("path1", "path")
            .putPathParams("path2", "path")
            .putPathParams("path3", "path")
            .putPathParams("path4", "path")
            .putQueryParams("query1", "query")
            .putQueryParams("query2", "query")
            .putQueryParams("query3", "query")
            .putQueryParams("query4", "query")
            .build();

    @Threads(1)
    @Benchmark
    public Request addUserAgentToEmpty() {
        return Request.builder()
                .from(empty)
                .putHeaderParams("user-agent", "hello")
                .build();
    }

    @Threads(1)
    @Benchmark
    public Request addUserAgentToNonEmpty() {
        return Request.builder()
                .from(nonEmpty)
                .putHeaderParams("user-agent", "hello")
                .build();
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(RequestBuilderBenchmark.class.getSimpleName())
                .jvmArgsPrepend("-Xmx1024m", "-Xms1024m", "-XX:+CrashOnOutOfMemoryError")
                // .jvmArgsPrepend("-XX:+FlightRecorder", "-XX:StartFlightRecording=filename=./foo.jfr")
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
