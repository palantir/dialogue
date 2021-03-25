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

package com.palantir.dialogue.annotations.processor;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.palantir.dialogue.annotations.Request;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.tools.Diagnostic.Kind;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public final class DialogueAnnotationsProcessor extends BasicAnnotationProcessor {

    @Override
    protected Iterable<? extends ProcessingStep> initSteps() {
        return ImmutableSet.of(new GenerateDialogueServiceFactory(processingEnv));
    }

    private static final class GenerateDialogueServiceFactory implements ProcessingStep {

        private final Messager messager;
        private final Filer filer;

        private GenerateDialogueServiceFactory(ProcessingEnvironment processingEnvironment) {
            this.messager = processingEnvironment.getMessager();
            this.filer = processingEnvironment.getFiler();
        }

        @Override
        public Set<? extends Class<? extends Annotation>> annotations() {
            return ImmutableSet.of(Request.class);
        }

        @Override
        public Set<Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
            Set<Element> elements = elementsByAnnotation.get(Request.class);
            if (elements.isEmpty()) {
                return ImmutableSet.of();
            }

            Set<Element> enclosingElements =
                    elements.stream().map(Element::getEnclosingElement).collect(Collectors.toSet());
            if (enclosingElements.size() > 1) {
                warning("Found multiple enclosing elements: " + enclosingElements);
                return ImmutableSet.of();
            }

            Element annotatedInterface = Iterables.getOnlyElement(enclosingElements);
            ElementKind kind = annotatedInterface.getKind();
            if (!kind.isInterface()) {
                error("Only methods on interfaces can be annotated");
                return ImmutableSet.of();
            }

            ClassName outputClass = ClassName.get(
                    MoreElements.getPackage(annotatedInterface)
                            .getQualifiedName()
                            .toString(),
                    annotatedInterface.getSimpleName().toString());

            TypeSpec generatedClass = new DialogueServiceFactoryGenerator(outputClass.simpleName()).generate();
            try {
                JavaFile.builder(outputClass.packageName(), generatedClass)
                        .build()
                        .writeTo(filer);
            } catch (IOException e) {
                throw new SafeRuntimeException("Could not generate", e);
            }
            return ImmutableSet.of();
        }

        private void error(String msg) {
            messager.printMessage(Kind.ERROR, msg);
        }

        private void warning(String msg) {
            messager.printMessage(Kind.WARNING, msg);
        }

        private void warning(String msg, Element element) {
            messager.printMessage(Kind.WARNING, msg, element);
        }

        private void note(String msg) {
            messager.printMessage(Kind.NOTE, msg);
        }
    }
}
