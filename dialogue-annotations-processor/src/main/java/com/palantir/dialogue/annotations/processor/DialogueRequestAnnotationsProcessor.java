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
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.common.streams.KeyedStream;
import com.palantir.dialogue.annotations.Request;
import com.palantir.dialogue.annotations.processor.data.EndpointDefinition;
import com.palantir.dialogue.annotations.processor.data.EndpointDefinitions;
import com.palantir.dialogue.annotations.processor.data.ImmutableServiceDefinition;
import com.palantir.dialogue.annotations.processor.data.ServiceDefinition;
import com.palantir.dialogue.annotations.processor.format.Goethe;
import com.palantir.dialogue.annotations.processor.generate.DialogueServiceFactoryGenerator;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeExceptions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public final class DialogueRequestAnnotationsProcessor extends AbstractProcessor {

    private Messager messager;
    private Filer filer;
    private Elements elements;
    private Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(Request.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_11;
    }

    @Override
    public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        KeyedStream.of(roundEnv.getElementsAnnotatedWith(Request.class))
                .map(e -> (Element) e)
                .mapKeys(Element::getEnclosingElement)
                .collectToSetMultimap()
                .asMap()
                .forEach((interfaceElement, annotatedMethods) -> {
                    JavaFile javaFile;
                    try {
                        javaFile = generateDialogueServiceFactory(interfaceElement, annotatedMethods);
                    } catch (Throwable e) {
                        error("Code generation failed", interfaceElement, e);
                        return;
                    }

                    try {
                        Goethe.formatAndEmit(javaFile, filer);
                    } catch (FilerException e) {
                        // Happens when same file is written twice. Dunno why this is a problem
                    } catch (IOException e) {
                        error("Failed to format generated code", interfaceElement, e);
                    }
                });

        return false;
    }

    private JavaFile generateDialogueServiceFactory(Element annotatedInterface, Collection<Element> annotatedMethods) {
        ElementKind kind = annotatedInterface.getKind();
        Preconditions.checkArgument(kind.isInterface(), "Only methods on interfaces can be annotated with @Request");

        Set<Element> nonMethodElements = annotatedMethods.stream()
                .filter(element -> !element.getKind().equals(ElementKind.METHOD))
                .collect(Collectors.toSet());
        validationStep(ctx -> nonMethodElements.forEach(
                nonMethodElement -> ctx.reportError("Only methods can be annotated with @Request", nonMethodElement)));

        Preconditions.checkArgument(nonMethodElements.isEmpty(), "Only methods can be annotated with @Request");

        List<EndpointDefinition> endpoints = processingStep(ctx -> {
            EndpointDefinitions endpointDefinitions = new EndpointDefinitions(ctx, elements, types);
            List<Optional<EndpointDefinition>> maybeEndpoints = annotatedMethods.stream()
                    .map(MoreElements::asExecutable)
                    .map(endpointDefinitions::tryParseEndpointDefinition)
                    .collect(Collectors.toList());

            Preconditions.checkArgument(
                    maybeEndpoints.stream()
                            .filter(Predicates.not(Optional::isPresent))
                            .collect(Collectors.toList())
                            .isEmpty(),
                    "Failed validation");
            return maybeEndpoints.stream().map(Optional::get).collect(Collectors.toList());
        });

        ClassName serviceInterface = ClassName.get(
                MoreElements.getPackage(annotatedInterface).getQualifiedName().toString(),
                annotatedInterface.getSimpleName().toString());

        ServiceDefinition serviceDefinition = ImmutableServiceDefinition.builder()
                .serviceInterface(serviceInterface)
                .addAllEndpoints(endpoints)
                .build();

        TypeSpec generatedClass = new DialogueServiceFactoryGenerator(serviceDefinition).generate();
        return JavaFile.builder(serviceInterface.packageName(), generatedClass).build();
    }

    private void validationStep(Consumer<DefaultErrorContext> validationFunction) {
        processingStep(ctx -> {
            validationFunction.accept(ctx);
            return null;
        });
    }

    private <T> T processingStep(Function<DefaultErrorContext, T> stepFunction) {
        try (DefaultErrorContext ctx = new DefaultErrorContext(messager)) {
            return stepFunction.apply(ctx);
        }
    }

    private void error(@CompileTimeConstant String msg, Element element, Throwable throwable) {
        printWithKind(Kind.ERROR, msg, element, throwable);
    }

    private void printWithKind(Kind kind, @CompileTimeConstant String msg, Element element, Throwable throwable) {
        String trace = Throwables.getStackTraceAsString(throwable);
        messager.printMessage(kind, msg + ": threw an exception " + trace, element);
    }

    private final class DefaultErrorContext implements AutoCloseable, ErrorContext {

        private final Messager messager;
        private volatile boolean errors = false;

        private DefaultErrorContext(Messager messager) {
            this.messager = messager;
        }

        @Override
        public void reportError(@CompileTimeConstant String message, Element element, Arg<?>... args) {
            tripWire();
            String renderedMessage = SafeExceptions.renderMessage(message, args);
            messager.printMessage(Kind.ERROR, renderedMessage, element);
        }

        @Override
        public void reportError(@CompileTimeConstant String message, Element element, Throwable throwable) {
            tripWire();
            error(message, element, throwable);
        }

        private void tripWire() {
            errors = true;
        }

        @Override
        public void close() {
            if (errors) {
                throw new SafeRuntimeException("There were errors");
            }
        }
    }
}
