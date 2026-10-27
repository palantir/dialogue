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
import java.util.stream.Stream;
import javax.net.ssl.SSLParameters;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for deprioritizing JEP 527's TLS 1.3 post-quantum hybrid key exchange schemes.
 * <p>
 * JEP 527 delivers 3 new key exchange schemes for TLS 1.3 that combine ML-KEM with traditional ECDHE. This was
 * delivered in JDK27, and backported to JDK25. As of JDK 25.0.5, the named group <pre>X25519MLKEM768</pre> is the default
 * hybrid key agreement scheme used, which combines X25519 with ML-KEM (JEP 527 adds two other hybrid schemes which
 * use different curves, but they are not enabled by default).
 * <p>
 * See:
 * <ul>
 *     <li><a href="https://bugs.openjdk.org/browse/JDK-8314323">JDK-8314323</a></li>
 *     <li><a href="https://github.com/openjdk/jdk25u/commit/83e90e7d76e9ca804384d47a4d529e91f79d8981">jdk25u commit 83e90e7d76</a></li>
 * </ul>
 * <p>
 * The hybrid scheme causes the JVM to send a ClientHello TLS handshake message containing information for both
 * X25519 and ML-KEM key exchange protocols. Unfortunately, some TLS implementations do not properly handle such
 * messages as they can be large enough to require the receiver to perform multiple TCP read operations. Correct
 * implementations should be capable of reading all the bytes in the ClientHello message, falling back to X25519 if
 * the server does not support ML-KEM; implementations that do not correctly handle this will instead fail the
 * handshake (see <a href="https://tldr.fail">tldr.fail</a>).
 * <p>
 * This class supports configuring an {@link SSLParameters} object so that <pre>X25519MLKEM768</pre> is moved to the
 * end of the configured named groups. JSSE sends key shares for at most two kinds of named group, taken in preference
 * order, so with the JDK defaults the initial ClientHello carries only the small {@code x25519} and {@code secp256r1}
 * key shares. {@code X25519MLKEM768} is still advertised in {@code supported_groups}, so a server that requires it can
 * ask for its key share with a HelloRetryRequest, at the cost of one extra round trip. This comes with a number of
 * drawbacks:
 * <ul>
 *     <li>servers that support both {@code X25519MLKEM768} and a traditional group, but do not insist on
 *         {@code X25519MLKEM768}, will typically choose the traditional group to avoid a HelloRetryRequest, so most
 *         connections will not use post-quantum key exchange.</li>
 *     <li>this relies on JSSE's undocumented limit of two key share kinds; if a future JDK sends more key shares, the
 *         large {@code X25519MLKEM768} key share may be sent in the initial ClientHello again.</li>
 *     <li>if fewer than two other kinds of named group precede {@code X25519MLKEM768} (for instance,
 *         {@code jdk.tls.namedGroups=x25519,X25519MLKEM768}), its key share is still sent in the initial
 *         ClientHello.</li>
 *     <li>the second ClientHello sent after a HelloRetryRequest is still large, so middleboxes between the client and
 *         server with the same bug may still fail the handshake.</li>
 *     <li>only {@code X25519MLKEM768} is reordered by this implementation, but other hybrid schemes are possible to
 *         configure which may result in similar issues</li>
 *     <li>the {@code X25519MLKEM768} scheme will be moved to the end even if it is explicitly configured first via the
 *         {@code jdk.tls.namedGroups} system property</li>
 * </ul>
 * <p>
 * Because we forcibly reorder the configured named groups, {@code X25519MLKEM768} cannot be made the preferred group
 * again using standard JSSE configuration options. Instead, setting the Dialogue-specific
 * {@code dialogue.tls.useJvmDefaultNamedGroupsOrdering} system property to {@code true} will bypass this logic and
 * use the named groups exactly as configured.
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

    // dialogue-specific system property to opt-out of reordering HYBRID_PQ_NAMED_GROUP in the configured namedGroups
    private static final String DIALOGUE_USE_JVM_DEFAULT_NAMED_GROUPS_ORDERING =
            "dialogue.tls.useJvmDefaultNamedGroupsOrdering";
    // Read once when this class is initialized, which happens on the first TLS handshake.
    private static final boolean IS_USE_JVM_DEFAULT_NAMED_GROUPS_ORDERING =
            Boolean.getBoolean(DIALOGUE_USE_JVM_DEFAULT_NAMED_GROUPS_ORDERING);

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
     * Moves {@link #HYBRID_PQ_NAMED_GROUP} to the end of the named groups the socket was configured with (via
     * {@code jdk.tls.namedGroups} or the JDK defaults), leaving the remaining groups and their order unchanged.
     * <p>
     * JSSE sends key shares for at most two kinds of named group, taken in preference order. With the JDK defaults,
     * {@code x25519} and {@code secp256r1} use up both, so the large hybrid key share is left out of the initial
     * ClientHello. The hybrid group is still listed in {@code supported_groups}, so a server that requires it can
     * request it with a HelloRetryRequest.
     */
    static void reorderPostQuantumHybridNamedGroup(SSLParameters sslParameters) {
        reorderPostQuantumHybridNamedGroup(sslParameters, IS_USE_JVM_DEFAULT_NAMED_GROUPS_ORDERING);
    }

    /**
     * As {@link #reorderPostQuantumHybridNamedGroup(SSLParameters)}, but with the opt-out passed explicitly rather
     * than read from the {@code dialogue.tls.useJvmDefaultNamedGroupsOrdering} system property.
     */
    @VisibleForTesting
    static void reorderPostQuantumHybridNamedGroup(
            SSLParameters sslParameters, boolean useJvmDefaultNamedGroupsOrdering) {
        // if the dialogue-specific system property to use the JVM's named groups ordering is enabled, then do
        // nothing; this reverts to the JVM's default behavior.
        if (useJvmDefaultNamedGroupsOrdering) {
            return;
        }

        String[] namedGroups = getNamedGroups(sslParameters);
        // Null means the runtime predates JDK 20, or the provider uses defaults we cannot inspect.
        if (namedGroups == null) {
            return;
        }
        String[] reordered = moveNamedGroupToEnd(namedGroups, HYBRID_PQ_NAMED_GROUP);
        if (!Arrays.equals(reordered, namedGroups)) {
            setNamedGroups(sslParameters, reordered);
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
     * Returns {@code namedGroups} with any entries matching {@code toMove} moved to the end, keeping the relative
     * order of all other groups.
     */
    @VisibleForTesting
    static String[] moveNamedGroupToEnd(String[] namedGroups, String toMove) {
        // JSSE matches named group names case-insensitively.
        return Stream.concat(
                        Arrays.stream(namedGroups).filter(group -> !toMove.equalsIgnoreCase(group)),
                        Arrays.stream(namedGroups).filter(toMove::equalsIgnoreCase))
                .toArray(String[]::new);
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
