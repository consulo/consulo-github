/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class IconRenderer extends GutterIconRenderer {
    private final NodeIcon myIcon;
    private final List<SyntaxAnnotation> myQuickFixes;
    private final PsiElement myPsiElement;

    public IconRenderer(@Nullable SyntaxAnnotation quickFix, PsiElement psiElement, NodeIcon icon) {
        this(quickFix, psiElement, icon, quickFix == null ? List.of() : List.of(quickFix));
    }

    public IconRenderer(@Nullable SyntaxAnnotation quickFix, PsiElement psiElement, NodeIcon icon, @Nullable List<SyntaxAnnotation> quickFixes) {
        myIcon = icon;
        myQuickFixes = quickFixes == null ? List.of() : quickFixes.stream()
            .filter(Objects::nonNull)
            .filter(SyntaxAnnotation::hasExecution)
            .distinct()
            .toList();
        myPsiElement = psiElement;
    }

    @Override
    public @Nullable AnAction getClickAction() {
        return myQuickFixes.size() == 1 ? new CustomClickAction(myQuickFixes.get(0), myPsiElement) : null;
    }

    @Override
    public @Nullable ActionGroup getPopupMenuActions() {
        if (myQuickFixes.size() <= 1) {
            return null;
        }
        DefaultActionGroup group = new DefaultActionGroup();
        myQuickFixes.forEach(fix -> group.add(new CustomClickAction(fix, myPsiElement)));
        return group;
    }

    @Override
    public Image getIcon() {
        return myIcon.icon();
    }

    @Override
    public boolean isNavigateAction() {
        return myQuickFixes.size() == 1;
    }

    @Override
    public LocalizeValue getTooltipValue() {
        if (myQuickFixes.isEmpty()) {
            return LocalizeValue.empty();
        }
        return LocalizeValue.of(myQuickFixes.stream()
            .map(fix -> fix.getText().get())
            .distinct()
            .collect(Collectors.joining("\n")));
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof IconRenderer;
    }

    @Override
    public int hashCode() {
        return IconRenderer.class.hashCode();
    }
}
