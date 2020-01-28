/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.example;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.ri.ResourceIdentifier;
import java.time.OffsetDateTime;
import java.util.List;

// Example of the interface code conjure would generate for a simple SampleService.
public interface AsyncSampleService {
    ListenableFuture<SampleObject> stringToString(
            String objectId, OffsetDateTime header, List<ResourceIdentifier> query, SampleObject body);

    ListenableFuture<Void> voidToVoid();
}
