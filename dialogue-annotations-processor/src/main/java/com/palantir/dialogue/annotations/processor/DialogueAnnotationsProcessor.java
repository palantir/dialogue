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

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.dialogue.annotations.Request;
import com.palantir.dialogue.annotations.processor.util.Goethe;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public final class DialogueAnnotationsProcessor extends AbstractProcessor {

    private Messager messager;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(Request.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        Set<Element> elements = roundEnv.getElementsAnnotatedWith(Request.class).stream()
                .map(e -> (Element) e)
                .collect(Collectors.toSet());
        if (elements.isEmpty()) {
            return false;
        }

        Element elementForErrorReporting = Iterables.getFirst(elements, null);

        Set<Element> enclosingElements =
                elements.stream().map(Element::getEnclosingElement).collect(Collectors.toSet());
        if (enclosingElements.size() > 1) {
            warning("Found multiple enclosing elements: " + enclosingElements, elementForErrorReporting);
            return false;
        }

        Element annotatedInterface = Iterables.getOnlyElement(enclosingElements);
        ElementKind kind = annotatedInterface.getKind();
        if (!kind.isInterface()) {
            error("Only methods on interfaces can be annotated");
            return false;
        }

        ClassName outputClass = ClassName.get(
                MoreElements.getPackage(annotatedInterface).getQualifiedName().toString(),
                annotatedInterface.getSimpleName().toString());

        TypeSpec generatedClass = new DialogueServiceFactoryGenerator(outputClass).generate();
        try {
            Goethe.formatAndEmit(
                    JavaFile.builder(outputClass.packageName(), generatedClass).build(), filer);
        } catch (IOException e) {
            error("Could not generate", elementForErrorReporting, e);
            return false;
        }

        return false;
    }

    private void error(String msg) {
        messager.printMessage(Kind.ERROR, msg);
    }

    private void error(String msg, Element element, Throwable throwable) {
        String trace = Throwables.getStackTraceAsString(throwable);
        messager.printMessage(Kind.ERROR, msg + ": threw an exception " + trace, element);
    }

    private void warning(String msg, Element element) {
        messager.printMessage(Kind.WARNING, msg, element);
    }
}
