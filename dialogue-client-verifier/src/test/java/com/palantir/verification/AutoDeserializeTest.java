/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.dialogue.com.palantir.conjure.verification.server.AutoDeserializeConfirmServiceBlocking;
import com.palantir.dialogue.com.palantir.conjure.verification.server.AutoDeserializeServiceBlocking;
import com.palantir.dialogue.com.palantir.conjure.verification.server.EndpointName;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AutoDeserializeTest {

    @ClassRule
    public static final VerificationServerRule server = new VerificationServerRule();

    private static final SafeLogger log = SafeLoggerFactory.get(AutoDeserializeTest.class);
    private static final AutoDeserializeServiceBlocking testService =
            server.client(AutoDeserializeServiceBlocking.class);
    private static final AutoDeserializeConfirmServiceBlocking confirmService =
            server.client(AutoDeserializeConfirmServiceBlocking.class);

    @Parameterized.Parameter(0)
    public EndpointName endpointName;

    @Parameterized.Parameter(1)
    public int index;

    @Parameterized.Parameter(2)
    public boolean shouldSucceed;

    @Parameterized.Parameter(3)
    public String jsonString;

    @Parameterized.Parameters(name = "{0}({3}) -> should succeed {2}")
    public static Collection<Object[]> data() {
        List<Object[]> objects = new ArrayList<>();
        Cases.TEST_CASES.getAutoDeserialize().forEach((endpointName, positiveAndNegativeTestCases) -> {
            int positiveSize = positiveAndNegativeTestCases.getPositive().size();
            int negativeSize = positiveAndNegativeTestCases.getNegative().size();

            IntStream.range(0, positiveSize)
                    .forEach(i -> objects.add(new Object[] {
                        endpointName,
                        i,
                        true,
                        positiveAndNegativeTestCases.getPositive().get(i)
                    }));

            IntStream.range(0, negativeSize)
                    .forEach(i -> objects.add(new Object[] {
                        endpointName,
                        positiveSize + i,
                        false,
                        positiveAndNegativeTestCases.getNegative().get(i)
                    }));
        });
        return objects;
    }

    @Test
    @SuppressWarnings("IllegalThrows")
    public void runTestCase() throws Error, NoSuchMethodException {
        boolean shouldIgnore = Cases.shouldIgnore(endpointName, jsonString);
        Method method = testService.getClass().getMethod(endpointName.get(), int.class);
        // Need to set accessible true work around dialogues anonymous class impl
        method.setAccessible(true);
        System.out.println(String.format(
                "[%s%s test case %s]: %s(%s), expected client to %s",
                shouldIgnore ? "ignored " : "",
                shouldSucceed ? "positive" : "negative",
                index,
                endpointName,
                jsonString,
                shouldSucceed ? "succeed" : "fail"));

        Optional<Error> expectationFailure = shouldSucceed ? expectSuccess(method) : expectFailure(method);

        if (shouldIgnore) {
            assertThat(expectationFailure)
                    .describedAs(
                            "The test passed but the test case was ignored - remove this from ignored-test-cases.yml")
                    .isNotEmpty();
        }

        Assume.assumeFalse(shouldIgnore);

        if (expectationFailure.isPresent()) {
            throw expectationFailure.get();
        }
    }

    private Optional<Error> expectSuccess(Method method) {
        try {
            Object resultFromServer = method.invoke(testService, index);
            log.info(
                    "Received result for endpoint {} and index {}: {}",
                    SafeArg.of("endpointName", endpointName),
                    SafeArg.of("index", index),
                    SafeArg.of("resultFromServer", resultFromServer));
            confirmService.confirm(endpointName, index, resultFromServer);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new AssertionError("Expected call to succeed, but caught exception", e));
        }
    }

    private Optional<Error> expectFailure(Method method) {
        try {
            Object result = method.invoke(testService, index);
            return Optional.of(new AssertionError(
                    String.format("Result should have caused an exception but deserialized to: %s", result)));
        } catch (Exception e) {
            return Optional.empty(); // we expected the method to throw and it did, so this expectation was satisifed
        }
    }
}
