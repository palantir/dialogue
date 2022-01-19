/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.dialogue.futures;

import com.google.common.util.concurrent.ListenableFuture;

public interface DialogueListenableFuture<V> extends ListenableFuture<V> {

    /**
     * Adds a callback to this future which will run if this future fails.
     * If the future is transformed, the ownership of the {@code onFailure} will be passed to the transformed future.
     * If currently owning future is completed with exception (or cancelled) the {@code onFailure} will be run.
     *
     * @param onFailure onFailure to manage
     */
    void failureCallback(Runnable onFailure);
}
