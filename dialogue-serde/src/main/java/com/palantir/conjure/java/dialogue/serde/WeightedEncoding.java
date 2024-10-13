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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.logsafe.Preconditions;
import java.util.Objects;

/**
 * Represents an encoding with a weight for <code>Accept</code> types.
 * Note that the weight may not be applied to the Accept header, rather
 * used to order values.
 */
public final class WeightedEncoding {

    private final Encoding encoding;
    private final double weight;

    private WeightedEncoding(Encoding encoding, double weight) {
        this.encoding = Preconditions.checkNotNull(encoding, "Encoding is required");
        this.weight = weight;
        Preconditions.checkArgument(weight >= 0 && weight <= 1, "Weight must be between zero and one (inclusive)");
    }

    static WeightedEncoding of(Encoding encoding, double weight) {
        return new WeightedEncoding(encoding, weight);
    }

    public static WeightedEncoding of(Encoding encoding) {
        return new WeightedEncoding(encoding, 1);
    }

    Encoding encoding() {
        return encoding;
    }

    double weight() {
        return weight;
    }

    @Override
    public String toString() {
        return "WeightedEncoding{encoding=" + encoding + ", weight=" + weight + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        WeightedEncoding that = (WeightedEncoding) other;
        return Double.compare(that.weight, weight) == 0 && encoding.equals(that.encoding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encoding, weight);
    }
}
