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

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Purely cosmetic - to make our graphs easier to understand. */
public final class EventMarkers {
    private final Ticker clock;
    private final Map<Long, String> events = new TreeMap<>();

    public EventMarkers(Ticker clock) {
        this.clock = clock;
    }

    void event(String string) {
        events.put(clock.read(), string);
    }

    public Map<Long, String> getEvents() {
        return Collections.unmodifiableMap(events);
    }
}
