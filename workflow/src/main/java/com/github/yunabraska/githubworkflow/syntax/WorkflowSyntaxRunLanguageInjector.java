/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.inject.MultiHostInjector;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;

@ExtensionImpl
public class WorkflowSyntaxRunLanguageInjector implements MultiHostInjector {
    @Override
    public Class<? extends PsiElement> getElementClass() {
        return YAMLScalar.class;
    }

    @Override
    public void injectLanguages(MultiHostRegistrar registrar, PsiElement context) {
        if (!(context instanceof YAMLScalar scalar) || !isRunScalar(scalar)) {
            return;
        }
        if (WorkflowYaml.getWorkflowFile(scalar).isEmpty()) {
            return;
        }
        languageForShell(scalar)
            .ifPresent(language -> inject(registrar, scalar, language));
    }

    private static boolean isRunScalar(YAMLScalar scalar) {
        return scalar.getParent() instanceof YAMLKeyValue keyValue && FIELD_RUN.equals(keyValue.getKeyText());
    }

    private static Optional<Language> languageForShell(YAMLScalar scalar) {
        return shellFor(scalar)
            .flatMap(shell -> languageIds(shell).stream()
                .map(Language::findLanguageByID)
                .filter(language -> language != null)
                .findFirst());
    }

    private static Optional<String> shellFor(YAMLScalar scalar) {
        return getParentStep(scalar)
            .flatMap(step -> getText(step, "shell"))
            .or(() -> getParentJob(scalar)
                .flatMap(job -> getChild(job, "defaults"))
                .flatMap(defaults -> getChild(defaults, FIELD_RUN))
                .flatMap(run -> getText(run, "shell")))
            .or(() -> getChild(scalar.getContainingFile(), "defaults")
                .flatMap(defaults -> getChild(defaults, FIELD_RUN))
                .flatMap(run -> getText(run, "shell")))
            .or(() -> Optional.of("bash"));
    }

    private static List<String> languageIds(String shell) {
        String normalized = shell.toLowerCase(Locale.ROOT).trim();
        if (normalized.contains("pwsh") || normalized.contains("powershell")) {
            return List.of("PowerShell");
        }
        if (normalized.contains("python")) {
            return List.of("Python");
        }
        if (normalized.contains("node") || normalized.contains("javascript") || normalized.equals("js")) {
            return List.of("JavaScript");
        }
        if (normalized.contains("ruby")) {
            return List.of("RUBY", "Ruby");
        }
        if (normalized.contains("perl")) {
            return List.of("Perl5", "Perl");
        }
        return List.of("Bash", "Shell Script");
    }

    private static void inject(MultiHostRegistrar registrar, YAMLScalar scalar, Language language) {
        List<TextRange> ranges = contentRanges(scalar);
        if (ranges.isEmpty()) {
            return;
        }
        registrar.startInjecting(language);
        ranges.forEach(range -> registrar.addPlace(null, null, scalar, range));
        registrar.doneInjecting();
    }

    private static List<TextRange> contentRanges(YAMLScalar scalar) {
        List<TextRange> ranges = WorkflowPsi.getContentRanges(scalar);
        List<TextRange> withoutExpressions = ranges.stream()
            .flatMap(range -> excludeWorkflowExpressions(scalar.getText(), range).stream())
            .toList();
        return subtractRanges(withoutExpressions, hereDocBodyRanges(scalar.getText(), new TextRange(0, scalar.getTextLength()))).stream()
            .filter(range -> range.getStartOffset() < range.getEndOffset())
            .toList();
    }

    private static List<TextRange> excludeWorkflowExpressions(String text, TextRange range) {
        List<TextRange> result = new ArrayList<>();
        int start = range.getStartOffset();
        while (start < range.getEndOffset()) {
            int expressionStart = text.indexOf("${{", start);
            if (expressionStart < 0 || expressionStart >= range.getEndOffset()) {
                result.add(new TextRange(start, range.getEndOffset()));
                break;
            }
            if (start < expressionStart) {
                result.add(new TextRange(start, expressionStart));
            }
            int expressionEnd = text.indexOf("}}", expressionStart + 3);
            start = expressionEnd < 0 ? range.getEndOffset() : Math.min(expressionEnd + 2, range.getEndOffset());
        }
        return result;
    }

    private static List<TextRange> hereDocBodyRanges(String text, TextRange range) {
        List<TextRange> result = new ArrayList<>();
        String delimiter = "";
        int bodyStart = -1;
        int lineStart = range.getStartOffset();
        while (lineStart < range.getEndOffset()) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? range.getEndOffset() : Math.min(newline, range.getEndOffset());
            String line = text.substring(lineStart, lineEnd);
            if (delimiter.isBlank()) {
                Optional<String> nextDelimiter = hereDocDelimiter(line);
                if (nextDelimiter.isPresent()) {
                    delimiter = nextDelimiter.get();
                    bodyStart = Math.min(lineEnd + 1, range.getEndOffset());
                }
            }
            else if (line.trim().equals(delimiter)) {
                if (bodyStart >= 0 && bodyStart < lineStart) {
                    result.add(new TextRange(bodyStart, lineStart));
                }
                delimiter = "";
                bodyStart = -1;
            }
            if (newline < 0 || lineEnd >= range.getEndOffset()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        if (!delimiter.isBlank() && bodyStart >= 0 && bodyStart < range.getEndOffset()) {
            result.add(new TextRange(bodyStart, range.getEndOffset()));
        }
        return result;
    }

    private static Optional<String> hereDocDelimiter(String line) {
        char quote = 0;
        for (int index = 0; index + 1 < line.length(); index++) {
            char current = line.charAt(index);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '<' && line.charAt(index + 1) == '<') {
                int delimiterStart = index + 2;
                if (delimiterStart < line.length() && line.charAt(delimiterStart) == '-') {
                    delimiterStart++;
                }
                while (delimiterStart < line.length() && Character.isWhitespace(line.charAt(delimiterStart))) {
                    delimiterStart++;
                }
                int delimiterEnd = delimiterStart;
                while (delimiterEnd < line.length() && isDelimiterChar(line.charAt(delimiterEnd))) {
                    delimiterEnd++;
                }
                if (delimiterStart < delimiterEnd) {
                    return Optional.of(line.substring(delimiterStart, delimiterEnd));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isDelimiterChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static List<TextRange> subtractRanges(List<TextRange> ranges, List<TextRange> excludedRanges) {
        List<TextRange> result = ranges;
        for (TextRange excludedRange : excludedRanges) {
            result = result.stream()
                .flatMap(range -> subtractRange(range, excludedRange).stream())
                .toList();
        }
        return result;
    }

    private static List<TextRange> subtractRange(TextRange range, TextRange excludedRange) {
        if (!range.intersectsStrict(excludedRange)) {
            return List.of(range);
        }
        List<TextRange> result = new ArrayList<>();
        if (range.getStartOffset() < excludedRange.getStartOffset()) {
            result.add(new TextRange(range.getStartOffset(), excludedRange.getStartOffset()));
        }
        if (excludedRange.getEndOffset() < range.getEndOffset()) {
            result.add(new TextRange(excludedRange.getEndOffset(), range.getEndOffset()));
        }
        return result;
    }
}
