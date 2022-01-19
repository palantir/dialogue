/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.dialogue.futures;

final class ResourceContext {

    private Runnable closeHandler;
    private boolean closed;

    private ResourceContext() {}

    synchronized ResourceContext onClose(Runnable onClose) {
        if (closed) {
            // This should not throw here: this needs to be somehow tied to the future?
            onClose.run();
        } else {
            closeHandler = compose(this.closeHandler, onClose);
        }
        return this;
    }

    synchronized void close() {
        // is this enough to protect against #close being called multiple times.
        if (!closed && closeHandler != null) {
            Runnable onClose = closeHandler;
            closeHandler = null;
            onClose.run();
        }
        closed = true;
    }

    static ResourceContext createEmpty() {
        return new ResourceContext();
    }

    private static Runnable compose(Runnable r1, Runnable r2) {
        if (r1 == null) {
            return r2;
        }
        return () -> {
            try {
                r1.run();
            } catch (Throwable t1) {
                try {
                    r2.run();
                } catch (Throwable t2) {
                    t1.addSuppressed(t2);
                }
                throw t1;
            }
            r2.run();
        };
    }
}
