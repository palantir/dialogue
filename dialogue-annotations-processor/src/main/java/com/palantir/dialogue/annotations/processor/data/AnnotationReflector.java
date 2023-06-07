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

import com.google.auto.common.MoreElements;
import com.palantir.common.streams.KeyedStream;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import org.immutables.value.Value;

@Value.Immutable
public interface AnnotationReflector {

    @Value.Parameter
    AnnotationMirror annotationMirror();

    @Value.Derived
    default TypeElement annotationTypeElement() {
        return MoreElements.asType(annotationMirror().getAnnotationType().asElement());
    }

    @Value.Derived
    default Map<String, ExecutableElement> methods() {
        return KeyedStream.of(ElementFilter.methodsIn(annotationTypeElement().getEnclosedElements()))
                .mapKeys(element -> element.getSimpleName().toString())
                .collectToMap();
    }

    @Value.Derived
    default Map<String, Object> values() {
        return KeyedStream.stream(annotationMirror().getElementValues())
                .mapKeys(key -> key.getSimpleName().toString())
                .map(AnnotationValue::getValue)
                .collectToMap();
    }

    default boolean isAnnotation(Class<? extends Annotation> annotationClazz) {
        return annotationTypeElement().getQualifiedName().contentEquals(annotationClazz.getCanonicalName());
    }

    default String getStringValueField() {
        return getValueStrict(String.class);
    }

    default <T> Optional<T> getValueFieldMaybe(Class<T> valueClazz) {
        return getFieldMaybe("value", valueClazz);
    }

    default <T> T getValueStrict(Class<T> valueClazz) {
        return getValueFieldMaybe(valueClazz).orElseThrow(() -> new SafeIllegalStateException("Unknown value"));
    }

    @SuppressWarnings("unchecked")
    default <T> Optional<T> getFieldMaybe(String fieldName, Class<T> valueClazz) {
        Preconditions.checkArgument(
                methods().containsKey(fieldName),
                "Unknown field",
                SafeArg.of("fieldName", fieldName),
                SafeArg.of("type", annotationTypeElement()),
                SafeArg.of("fields", methods()));
        Optional<Object> maybeValue = Optional.ofNullable(values().get(fieldName));
        return maybeValue.map(value -> {
            Preconditions.checkArgument(
                    valueClazz.isInstance(value),
                    "Value not of the right type",
                    SafeArg.of("fieldName", fieldName),
                    SafeArg.of("type", annotationTypeElement()),
                    SafeArg.of("fields", methods()),
                    SafeArg.of("expected", valueClazz),
                    SafeArg.of("actual", value.getClass()));
            return (T) value;
        });
    }
}
