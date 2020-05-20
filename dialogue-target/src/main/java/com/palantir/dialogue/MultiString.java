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

package com.palantir.dialogue;

import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Stores a non-zero collection strings by sticking them into a single pipe delimited string, e.g. "hello|world". Unable
 * to store an empty collection.
 */
final class MultiString {

    private static final char SEP = '|';
    private static final char ESCAPE = '^';

    private static boolean noSpecialChars(String head) {
        return head.indexOf(SEP) == -1 && head.indexOf(ESCAPE) == -1;
    }

    public static String encode(List<String> strings) {
        Preconditions.checkArgument(!strings.isEmpty(), "Can't encode an empty list");
        int numStrings = strings.size();

        if (numStrings == 1) {
            String head = strings.get(0);
            if (noSpecialChars(head)) {
                return head;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numStrings; i++) {
            String input = strings.get(i);
            if (i > 0) {
                sb.append(SEP);
            }

            if (noSpecialChars(input)) {
                sb.append(input);
            } else {
                for (char c : input.toCharArray()) {
                    if (c == SEP || c == ESCAPE) {
                        sb.append(ESCAPE);
                    }
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public static String encode(Iterable<? extends String> strings) {
        if (strings instanceof List) {
            return encode((List<String>) strings);
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String input : strings) {
            if (!first) {
                sb.append(SEP);
            }
            first = false;

            if (noSpecialChars(input)) {
                sb.append(input);
            } else {
                for (char c : input.toCharArray()) {
                    if (c == SEP || c == ESCAPE) {
                        sb.append(ESCAPE);
                    }
                    sb.append(c);
                }
            }
        }
        Preconditions.checkState(!first, "Can't encode an empty iterable");

        return sb.toString();
    }

    public static int decodeCount(String multiString) {
        if (noSpecialChars(multiString)) {
            return 1;
        }

        int count = 0;

        boolean inEscape = false;
        for (char c : multiString.toCharArray()) {
            if (inEscape) {
                inEscape = false;
            } else if (c == SEP) {
                count += 1;
            } else if (c == ESCAPE) {
                inEscape = true;
            }
        }

        count += 1;
        return count;
    }

    public static List<String> decode(String multiString) {
        if (noSpecialChars(multiString)) {
            // we often store a single value with no separator and no unescaping necessary
            return Collections.singletonList(multiString);
        }

        List<String> tokens = new ArrayList<>(2);
        StringBuilder sb = new StringBuilder();

        boolean inEscape = false;
        for (char c : multiString.toCharArray()) {
            if (inEscape) {
                inEscape = false;
                sb.append(c);
                continue;
            }

            if (c == SEP) {
                tokens.add(sb.toString());
                sb.setLength(0);
                continue;
            }

            if (c == ESCAPE) {
                inEscape = true; // don't copy char, move on
                continue;
            }

            sb.append(c);
        }

        tokens.add(sb.toString());

        return tokens;
    }

    public static String concat(String head, String tail) {
        return head + SEP + tail;
    }
}
