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

package com.palantir.dialogue.hc5;

import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
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
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
public class RoutePlannerBenchmark {

    private final DialogueRoutePlanner planner = new DialogueRoutePlanner(ProxySelector.getDefault());
    private final String hostName = "some-service.example.com";
    private final HttpHost sharedHost = new HttpHost("https", hostName, 8443);
    private final HttpClientContext context = HttpClientContext.create();

    /** Like real requests, which create an equal but distinct target host each time. */
    @Threads(1)
    @Benchmark
    public HttpRoute determineRoute() throws HttpException {
        return planner.determineRoute(new HttpHost("https", hostName, 8443), context);
    }

    @Threads(1)
    @Benchmark
    public HttpRoute determineRouteSharedHost() throws HttpException {
        return planner.determineRoute(sharedHost, context);
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(RoutePlannerBenchmark.class.getSimpleName())
                .jvmArgsPrepend("-Xmx1024m", "-Xms1024m", "-XX:+CrashOnOutOfMemoryError")
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
