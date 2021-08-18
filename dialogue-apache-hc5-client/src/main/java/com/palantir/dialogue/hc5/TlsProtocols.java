/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.primitives.Ints;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.random.SafeThreadLocalRandom;

/** Internal utility functionality to slowly roll out new TLS protocol support. */
final class TlsProtocols {

    private static final SafeLogger log = SafeLoggerFactory.get(TlsProtocols.class);
    private static final boolean JAVA_15_OR_LATER = isJava15OrLater();
    private static final String TLS_V1_2 = "TLSv1.2";
    private static final String TLS_V1_3 = "TLSv1.3";

    static String[] enabledFor(String clientName) {
        if (JAVA_15_OR_LATER && (assertsEnabled() || SafeThreadLocalRandom.get().nextDouble() < .1D)) {
            log.info("Enabling TLSv1.3 support for client '{}'", SafeArg.of("client", clientName));
            return new String[] {TLS_V1_3, TLS_V1_2};
        } else {
            return new String[] {TLS_V1_2};
        }
    }

    private static boolean isJava15OrLater() {
        Integer version = Ints.tryParse(System.getProperty("java.specification.version"));
        return version != null && version >= 15;
    }

    @SuppressWarnings({"AssertWithSideEffects", "ConstantConditions", "InnerAssignment", "BadAssert"})
    private static boolean assertsEnabled() {
        boolean ret = false;
        assert (ret = true);
        return ret;
    }

    private TlsProtocols() {}
}
