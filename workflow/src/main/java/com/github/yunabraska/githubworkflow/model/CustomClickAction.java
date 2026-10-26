/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getProject;

public class CustomClickAction extends AnAction {
    private final SyntaxAnnotation myQuickFix;
    private final PsiElement myPsiElement;

    public CustomClickAction(SyntaxAnnotation quickFix, PsiElement psiElement) {
        super(quickFix.getText());
        myQuickFix = quickFix;
        myPsiElement = psiElement;
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = getProject(myPsiElement);
        if (project != null && myQuickFix.isAvailable(project, null, myPsiElement.getContainingFile())) {
            myQuickFix.invoke(project, null, myPsiElement.getContainingFile());
        }
    }
}
