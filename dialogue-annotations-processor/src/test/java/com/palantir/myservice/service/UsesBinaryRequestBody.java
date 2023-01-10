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

package com.palantir.myservice.service;

import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.annotations.Request;

// Annotation processor is not invoked directly here.
// @DialogueService(MyServiceDialogueServiceFactory.class)
public interface UsesBinaryRequestBody {

    @Request(method = HttpMethod.PUT, path = "/conjure/binary/request")
    void conjureBinaryRequest(@Request.Body BinaryRequestBody requestBody);
}
