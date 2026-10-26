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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import javax.net.ssl.SSLParameters;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for disabling JEP 527's TLS 1.3 post-quantum hybrid key exchange schemes.
 * <p>
 * JEP 527 delivers 3 new key exchange schemes for TLS 1.3 that combine ML-KEM with traditional ECDHE. This was
 * delivered in JDK27, and backported to JDK25. As of JDK 25.0.5, the named group <pre>X25519MLKEM768</pre> is the default
 * hybrid key agreement scheme used, which combines X25519 with ML-KEM (JEP 527 adds two other hybrid schemes which
 * use different curves, but they are not enabled by default).
 * <p>
 * The hybrid scheme causes the JVM to send a ClientHello TLS handshake message containing information for both
 * X25519 and ML-KEM key exchange protocols. Unfortunately, some TLS implementations do not properly handle such
 * messages as they can be large enough to require the receiver to perform multiple TCP read operations. Correct
 * implementations should be capable of reading all the bytes in the ClientHello message, falling back to X25519 if
 * the server does not support ML-KEM; implementations that do not correctly handle this will instead fail the
 * handshake (see <a href="https://tldr.fail">tldr.fail</a>).
 * <p>
 * This class supports configuring an {@link SSLParameters} object with named groups that will explicitly exclude
 * <pre>X25519MLKEM768</pre> to avoid this problem entirely. However, this comes with a number of drawbacks:
 * <ul>
 *     <li>disabling {@code X25519MLKEM768} weakens the security posture of the TLS connection, as it means we cannot
 *         negotiate a shared key using a post-quantum algorithm at all, regardless on if the server supports it.</li>
 *     <li>only {@code X25519MLKEM768} is ignored by this implementation, but other hybrid schemes are possible to
 *         configure which may result in similar issues</li>
 *     <li>the {@code X25519MLKEM768} scheme will be ignored even if it is explicitly configured via the
 *         {@code jdk.tls.namedGroups} system property</li>
 * </ul>
 * <p>
 * Because we forcibly exclude {@code X25519MLKEM768} from the configured named groups, it cannot be explicitly turned
 * on again using tandard JSSE configuration options. Instead, setting the Dialogue-specific
 * {@code dialogue.tls.allowPostQuantumHybridKeyExchange} system property to {@code true} will bypass this logic and
 * allow {@code X25519MLKEM768} to be used, if configured.
 * <p>
 * A future change should look at allowing post-quantum key exchange algorithms at first, and falling back to
 * traditional ECDHE if the handshake experiences a timeout, as this would allow for ML-KEM to be optimistically
 * chosen if supported. Long-term, TLS implementations should be updated to support post-quantum algorithms
 * correctly.
 * <p>
 * Because assigning named groups to an {@link SSLParameters} is only supported on JDK20 and above, this implementation
 * uses reflection. On JDK19 or below, this becomes a no-op.
 */
final class DialogueTlsPostQuantumHybridKeyExchange {

    private static final SafeLogger log = SafeLoggerFactory.get(DialogueTlsPostQuantumHybridKeyExchange.class);

    // dialogue-specific system property to opt-out of excluding HYBRID_PQ_NAMED_GROUP from the configured namedGroups
    private static final String DIALOGUE_ALLOW_POST_QUANTUM_HYBRID_KEY_EXCHANGE =
            "dialogue.tls.allowPostQuantumHybridKeyExchange";
    // Read once when this class is initialized, which happens on the first TLS handshake.
    private static final boolean IS_ALLOW_POST_QUANTUM_HYBRID_KEY_EXCHANGE =
            Boolean.getBoolean(DIALOGUE_ALLOW_POST_QUANTUM_HYBRID_KEY_EXCHANGE);

    // Hybrid post-quantum key exchange added by JEP 527. Its key share makes the ClientHello large enough to span
    // multiple TCP packets, which some servers and middleboxes fail to handle (see https://tldr.fail/).
    @VisibleForTesting
    static final String HYBRID_PQ_NAMED_GROUP = "X25519MLKEM768";

    // SSLParameters#getNamedGroups and #setNamedGroups were added in JDK 20, but we compile against release 17.
    private static final @Nullable MethodHandle GET_NAMED_GROUPS =
            findSslParametersMethod("getNamedGroups", MethodType.methodType(String[].class));
    private static final @Nullable MethodHandle SET_NAMED_GROUPS =
            findSslParametersMethod("setNamedGroups", MethodType.methodType(void.class, String[].class));

    /**
     * Removes {@link #HYBRID_PQ_NAMED_GROUP} from the named groups the socket was configured with (via
     * {@code jdk.tls.namedGroups} or the JDK defaults), leaving the remaining groups and their order unchanged.
     */
    static void excludePostQuantumHybridNamedGroup(SSLParameters sslParameters) {
        excludePostQuantumHybridNamedGroup(sslParameters, IS_ALLOW_POST_QUANTUM_HYBRID_KEY_EXCHANGE);
    }

    /**
     * As {@link #excludePostQuantumHybridNamedGroup(SSLParameters)}, but with the opt-out passed explicitly rather
     * than read from the {@code dialogue.tls.allowPostQuantumHybridKeyExchange} system property.
     */
    @VisibleForTesting
    static void excludePostQuantumHybridNamedGroup(
            SSLParameters sslParameters, boolean allowPostQuantumHybridKeyExchange) {
        // if the dialogue-specific system property to allow post-quantum hybrid key exchange schemes is enabled,
        // then do nothing; this reverts to the JVM's default behavior.
        if (allowPostQuantumHybridKeyExchange) {
            return;
        }

        String[] namedGroups = getNamedGroups(sslParameters);
        // Null means the runtime predates JDK 20, or the provider uses defaults we cannot inspect.
        if (namedGroups == null) {
            return;
        }
        String[] filtered = removeNamedGroup(namedGroups, HYBRID_PQ_NAMED_GROUP);
        if (filtered.length != namedGroups.length) {
            setNamedGroups(sslParameters, filtered);
        }
    }

    /** Invokes {@code SSLParameters#getNamedGroups}, returning null if the runtime does not support it. */
    @VisibleForTesting
    static String @Nullable [] getNamedGroups(SSLParameters sslParameters) {
        if (GET_NAMED_GROUPS == null) {
            return null;
        }
        try {
            return (String[]) GET_NAMED_GROUPS.invokeExact(sslParameters);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            // getNamedGroups does not declare checked exceptions.
            throw new SafeIllegalStateException("Failed to read TLS named groups", t);
        }
    }

    /** Invokes {@code SSLParameters#setNamedGroups}. Only call when {@link #getNamedGroups} returned non-null. */
    @VisibleForTesting
    static void setNamedGroups(SSLParameters sslParameters, String[] namedGroups) {
        Preconditions.checkState(SET_NAMED_GROUPS != null, "SSLParameters#setNamedGroups is unavailable");
        try {
            SET_NAMED_GROUPS.invokeExact(sslParameters, namedGroups);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            // setNamedGroups does not declare checked exceptions.
            throw new SafeIllegalStateException("Failed to configure TLS named groups", t);
        }
    }

    /**
     * Returns {@code namedGroups} without {@code toRemove}. Returns the input unchanged if removal would leave no
     * groups, since an empty array disables every key exchange and fails all handshakes: if the JVM was explicitly
     * configured with only {@code toRemove}, we respect that.
     */
    @VisibleForTesting
    static String[] removeNamedGroup(String[] namedGroups, String toRemove) {
        // JSSE matches named group names case-insensitively.
        String[] filtered = Arrays.stream(namedGroups)
                .filter(group -> !toRemove.equalsIgnoreCase(group))
                .toArray(String[]::new);
        return filtered.length == 0 ? namedGroups : filtered;
    }

    private static @Nullable MethodHandle findSslParametersMethod(String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(SSLParameters.class, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            log.debug(
                    "SSLParameters method is unavailable, TLS named groups will not be modified",
                    SafeArg.of("method", name),
                    e);
            return null;
        }
    }

    private DialogueTlsPostQuantumHybridKeyExchange() {}
}
