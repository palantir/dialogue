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

package com.palantir.dialogue.annotations.processor.data;

import com.squareup.javapoet.ClassName;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(stagedBuilder = true)
public interface ServiceDefinition {
    ClassName serviceInterface();

    @Value.Derived
    default ClassName serviceFactory() {
        return ClassName.get(
                serviceInterface().packageName(), serviceInterface().simpleName() + "DialogueServiceFactory");
    }

    @Value.Derived
    default ClassName endpointsEnum() {
        return serviceFactory().nestedClass("Endpoints");
    }

    @Value.Derived
    default String endpointChannelFactoryArgName() {
        return "endpointChannelFactory";
    }

    @Value.Derived
    default String conjureRuntimeArgName() {
        return "runtime";
    }

    List<EndpointDefinition> endpoints();
}
