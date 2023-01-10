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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.ByteSource;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.myservice.service.MultipleParamAnnotations;
import com.palantir.myservice.service.MyService;
import com.palantir.myservice.service.RequestAnnotatedClass;
import com.palantir.myservice.service.UnparseableHttpPath;
import com.palantir.myservice.service.UsesBinaryRequestBody;
import com.palantir.myservice.service.WrongFactoryAnnotation;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

public final class DialogueRequestAnnotationsProcessorTest {

    private static final boolean DEV_MODE = Boolean.valueOf(System.getProperty("recreate", "false"));
    private static final Path TEST_CLASSES_BASE_DIR = Paths.get("src", "test", "java");
    private static final Path RESOURCES_BASE_DIR = Paths.get("src", "test", "resources");

    @Test
    public void testExampleFileCompiles() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, MyService.class);
    }

    @Test
    public void testCannotAnnotateClass() {
        assertThat(compileTestClass(TEST_CLASSES_BASE_DIR, RequestAnnotatedClass.class))
                .hadErrorContaining("Only methods on interfaces can be annotated");
    }

    @Test
    public void testCannotAnnotateParamsWithMultipleAnnotations() {
        Compilation compilation = compileTestClass(TEST_CLASSES_BASE_DIR, MultipleParamAnnotations.class);
        assertThat(compilation)
                .hadErrorContaining("Only single annotation can be used")
                .inFile(compilation.sourceFiles().get(0))
                .onLineContaining("String greet(@Request.PathParam @Request.Body String greeting)");
        assertThat(compilation).hadErrorCount(2);
    }

    @Test
    public void testWrongFactoryAnnotation() {
        Compilation compilation = compileTestClass(TEST_CLASSES_BASE_DIR, WrongFactoryAnnotation.class);
        assertThat(compilation)
                .hadErrorContaining("@DialogueService annotation references an invalid DialogueServiceFactory")
                .inFile(compilation.sourceFiles().get(0));
        assertThat(compilation).hadErrorCount(1);
    }

    @Test
    public void testHttpPathIsParsed() {
        Compilation compilation = compileTestClass(TEST_CLASSES_BASE_DIR, UnparseableHttpPath.class);
        assertThat(compilation)
                .hadErrorContaining("Failed to parse http path: threw an exception java.lang.IllegalArgumentException: "
                        + "Invalid syntax in the template \"/greet/{oops\". "
                        + "Check if a path parameter is terminated with a \"}\".")
                .inFile(compilation.sourceFiles().get(0))
                .onLineContaining("String greet(@Request.PathParam String greeting)");
        assertThat(compilation).hadErrorCount(2);
    }

    @Test
    public void testCannotUseConjureRequestBody() {
        Compilation compilation = compileTestClass(TEST_CLASSES_BASE_DIR, UsesBinaryRequestBody.class);
        assertThat(compilation).hadErrorContaining("prefer the more expressive RequestBody type");
    }

    private void assertTestFileCompileAndMatches(Path basePath, Class<?> clazz) {
        Compilation compilation = compileTestClass(basePath, clazz);
        assertThat(compilation).succeededWithoutWarnings();
        String generatedClassName = clazz.getSimpleName() + "DialogueServiceFactory";
        String generatedFqnClassName = clazz.getPackage().getName() + "." + generatedClassName;
        String generatedClassFileRelativePath = generatedFqnClassName.replaceAll("\\.", "/") + ".java";
        assertThat(compilation.generatedFile(StandardLocation.SOURCE_OUTPUT, generatedClassFileRelativePath))
                .hasValueSatisfying(
                        javaFileObject -> assertContentsMatch(javaFileObject, generatedClassFileRelativePath));
    }

    private Compilation compileTestClass(Path basePath, Class<?> clazz) {
        Path clazzPath = basePath.resolve(Paths.get(
                Joiner.on("/").join(Splitter.on(".").split(clazz.getPackage().getName())),
                clazz.getSimpleName() + ".java"));
        try {
            return Compiler.javac()
                    // This is required because this tool does not know about our gradle setting.
                    .withOptions("-source", "11")
                    .withOptions("-Werror")
                    .withOptions("-Xlint:unchecked")
                    .withProcessors(new DialogueRequestAnnotationsProcessor())
                    .compile(JavaFileObjects.forResource(clazzPath.toUri().toURL()));
        } catch (MalformedURLException e) {
            throw new SafeRuntimeException(e);
        }
    }

    private void assertContentsMatch(JavaFileObject javaFileObject, String generatedClassFile) {
        try {
            Path output = RESOURCES_BASE_DIR.resolve(generatedClassFile + ".generated");
            String generatedContents = readJavaFileObject(javaFileObject);
            if (DEV_MODE) {
                Files.deleteIfExists(output);
                Files.write(output, generatedContents.getBytes(StandardCharsets.UTF_8));
            }
            assertThat(generatedContents).isEqualTo(readFromFile(output));
        } catch (IOException e) {
            throw new SafeRuntimeException(e);
        }
    }

    private String readJavaFileObject(JavaFileObject javaFileObject) throws IOException {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return javaFileObject.openInputStream();
            }
        }.asCharSource(StandardCharsets.UTF_8).read();
    }

    private static String readFromFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
