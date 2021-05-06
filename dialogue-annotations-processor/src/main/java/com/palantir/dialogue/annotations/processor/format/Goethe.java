/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations.processor.format;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.FormatterDiagnostic;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.squareup.javapoet.JavaFile;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

public final class Goethe {

    private static final Formatter JAVA_FORMATTER = Formatter.createFormatter(JavaFormatterOptions.builder()
            .style(JavaFormatterOptions.Style.PALANTIR)
            .build());

    private Goethe() {}

    public static void formatAndEmit(JavaFile file, Filer filer, Element element) throws IOException {
        StringBuilder unformattedSource = new StringBuilder();
        file.writeTo(unformattedSource);
        try {
            String formattedSource = JAVA_FORMATTER.formatSource(unformattedSource.toString());
            writeTo(getFileName(file), formattedSource, filer, element);
        } catch (FormatterException e) {
            throw new IOException(generateMessage(file, unformattedSource.toString(), e.diagnostics()), e);
        }
    }

    private static String generateMessage(
            JavaFile file, String unformattedSource, List<FormatterDiagnostic> formatterDiagnostics) {
        try {
            List<String> lines = Splitter.on('\n').splitToList(unformattedSource);
            StringBuilder failureText = new StringBuilder();
            failureText
                    .append("Failed to format '")
                    .append(file.packageName)
                    .append('.')
                    .append(file.typeSpec.name)
                    .append("'\n");
            for (FormatterDiagnostic formatterDiagnostic : formatterDiagnostics) {
                failureText
                        .append(formatterDiagnostic.message())
                        .append("\n")
                        // Diagnostic values are one-indexed, while our list is zero-indexed.
                        .append(lines.get(formatterDiagnostic.line() - 1))
                        .append('\n')
                        // Offset by two to convert from one-indexed to zero indexed values, and account for the caret.
                        .append(Strings.repeat(" ", Math.max(0, formatterDiagnostic.column() - 2)))
                        .append("^\n\n");
            }
            return CharMatcher.is('\n').trimFrom(failureText.toString());
        } catch (RuntimeException e) {
            return "Failed to format:\n" + unformattedSource;
        }
    }

    private static String getFileName(JavaFile file) {
        return file.packageName.isEmpty() ? file.typeSpec.name : file.packageName + "." + file.typeSpec.name;
    }

    @SuppressWarnings({"EmptyCatch", "EmptyCatchBlock"})
    private static void writeTo(String fileName, String sourceCode, Filer filer, Element element) throws IOException {
        JavaFileObject filerSourceFile = filer.createSourceFile(fileName, element);
        try (Writer writer = filerSourceFile.openWriter()) {
            writer.write(sourceCode);
        } catch (Exception e) {
            try {
                filerSourceFile.delete();
            } catch (Exception _ignored) {
            }
            throw e;
        }
    }
}
