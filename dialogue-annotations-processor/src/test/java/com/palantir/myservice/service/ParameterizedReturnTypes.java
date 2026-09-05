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

package com.palantir.myservice.service;

import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.annotations.Request;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ParameterizedReturnTypes {

    @Request(method = HttpMethod.GET, path = "/list")
    List<String> list();

    @Request(method = HttpMethod.GET, path = "/set")
    Set<String> set();

    @Request(method = HttpMethod.GET, path = "/optional")
    Optional<String> optional();

    @Request(method = HttpMethod.GET, path = "/map")
    Map<String, Integer> map();

    @Request(method = HttpMethod.GET, path = "/unsupported-outer-type")
    Collection<String> unsupportedOuterType();

    @Request(method = HttpMethod.GET, path = "/nested-parameterization")
    List<Optional<String>> nestedParameterization();
}
