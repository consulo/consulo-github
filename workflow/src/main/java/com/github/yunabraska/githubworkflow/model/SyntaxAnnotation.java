/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.codeEditor.Editor;
import consulo.component.util.Iconable;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class SyntaxAnnotation implements IntentionAction, Iconable {
    private final LocalizeValue myText;
    private final @Nullable NodeIcon myIcon;
    private final HighlightSeverity myLevel;
    private final @Nullable ProblemHighlightType myType;
    private final @Nullable Consumer<QuickFixExecution> myExecute;
    private final boolean myShowToolTip;

    public SyntaxAnnotation(LocalizeValue text, @Nullable NodeIcon icon, @Nullable Consumer<QuickFixExecution> execute) {
        this(text, icon, HighlightSeverity.ERROR, ProblemHighlightType.GENERIC_ERROR, execute);
    }

    public SyntaxAnnotation(
        LocalizeValue text,
        @Nullable NodeIcon icon,
        HighlightSeverity level,
        @Nullable ProblemHighlightType type,
        @Nullable Consumer<QuickFixExecution> execute
    ) {
        this(text, icon, level, type, execute, true);
    }

    public SyntaxAnnotation(
        LocalizeValue text,
        @Nullable NodeIcon icon,
        HighlightSeverity level,
        @Nullable ProblemHighlightType type,
        @Nullable Consumer<QuickFixExecution> execute,
        boolean showToolTip
    ) {
        myText = text;
        myIcon = icon;
        myLevel = level;
        myType = type;
        myExecute = execute;
        myShowToolTip = showToolTip;
    }

    @Override
    public void invoke(Project project, @Nullable Editor editor, @Nullable PsiFile file) throws IncorrectOperationException {
        ofNullable(myExecute).ifPresent(projectConsumer -> projectConsumer.accept(new QuickFixExecution(this, project, editor, file)));
    }

    public void createAnnotation(PsiElement psiElement, AnnotationHolder holder) {
        createAnnotation(psiElement, psiElement.getTextRange(), holder);
    }

    public void createAnnotation(PsiElement psiElement, @Nullable TextRange range, AnnotationHolder holder) {
        createAnnotation(psiElement, range, holder, List.of(this));
    }

    public static void createAnnotation(
        @Nullable PsiElement psiElement,
        @Nullable TextRange range,
        @Nullable AnnotationHolder holder,
        @Nullable List<SyntaxAnnotation> fixes
    ) {
        if (fixes != null && !fixes.isEmpty() && holder != null && psiElement != null && psiElement.isValid()) {
            fixes.stream().collect(Collectors.groupingBy(f -> f.myLevel)).forEach((level, group) -> {
                SyntaxAnnotation firstItem = group.get(0);
                AnnotationBuilder annotation = holder.newAnnotation(level, firstItem.myShowToolTip ? firstItem.myText : LocalizeValue.empty());
                annotation = annotation.range(range != null ? range : psiElement.getTextRange());
                if (firstItem.myType != null) {
                    annotation = annotation.highlightType(firstItem.myType);
                }
                if (firstItem.myShowToolTip) {
                    annotation = annotation.tooltip(firstItem.myText);
                }
                for (SyntaxAnnotation fix : group) {
                    if (fix.myExecute != null) {
                        annotation = annotation.withFix(fix);
                    }
                }
                annotation.create();
            });
        }
    }

    public @Nullable Image icon() {
        return myIcon == null ? null : myIcon.icon();
    }

    @Override
    public @Nullable Image getIcon(int flags) {
        return myIcon == null ? null : myIcon.icon();
    }

    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Override
    public boolean isAvailable(Project project, @Nullable Editor editor, @Nullable PsiFile file) {
        return true;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    boolean hasExecution() {
        return myExecute != null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SyntaxAnnotation syntaxAnnotation = (SyntaxAnnotation) o;
        return Objects.equals(getText().get(), syntaxAnnotation.getText().get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getText().get());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SyntaxAnnotation.class.getSimpleName() + "[", "]")
            .add("text='" + myText + "'")
            .add("icon=" + myIcon)
            .add("level=" + myLevel)
            .add("type=" + myType)
            .add("execute=" + myExecute)
            .add("showToolTip=" + myShowToolTip)
            .toString();
    }
}
