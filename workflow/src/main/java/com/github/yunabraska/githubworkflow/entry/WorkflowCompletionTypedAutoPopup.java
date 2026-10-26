/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getWorkflowFile;

/**
 * Opens workflow completion while the user types YAML structure and expression separators.
 */
@ExtensionImpl(id = "githubWorkflowTypedAutoPopup")
public class WorkflowCompletionTypedAutoPopup extends TypedHandlerDelegate {
    @Override
    public Result checkAutoPopup(char typeChar, Project project, Editor editor, PsiFile file) {
        return Result.CONTINUE;
    }

    @Override
    public Result charTyped(char typeChar, Project project, Editor editor, PsiFile file) {
        if (shouldAutoPopup(typeChar, editor, file)) {
            scheduleWorkflowPopup(project, editor, file);
        }
        return Result.CONTINUE;
    }

    static void scheduleWorkflowPopup(Project project, Editor editor, PsiFile file) {
        if (!WorkflowYaml.isWorkflowElement(file)) {
            return;
        }
        project.getUIAccess().give(() -> {
            if (project.isDisposed() || editor.isDisposed()) {
                return;
            }
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        });
    }

    public static boolean shouldAutoPopup(char typeChar, @Nullable Editor editor, @Nullable PsiFile file) {
        if (!WorkflowCompletion.workflowCompletionTrigger(typeChar) || editor == null || file == null) {
            return false;
        }
        int textLength = file.getTextLength();
        if (textLength <= 0) {
            return getWorkflowFile(file).isPresent();
        }
        int offset = Math.max(0, Math.min(editor.getCaretModel().getOffset(), textLength - 1));
        PsiElement element = Optional.<PsiElement>ofNullable(file.findElementAt(offset)).orElse(file);
        return getWorkflowFile(element).isPresent();
    }
}
