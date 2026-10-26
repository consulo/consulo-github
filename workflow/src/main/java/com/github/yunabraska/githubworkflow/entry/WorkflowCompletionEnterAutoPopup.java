/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.entry;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.action.EnterHandlerDelegateAdapter;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getWorkflowFile;

/**
 * Opens workflow key completion after pressing Enter below YAML mapping keys.
 */
@ExtensionImpl(id = "githubWorkflowEnterAutoPopup")
public class WorkflowCompletionEnterAutoPopup extends EnterHandlerDelegateAdapter {
    @Override
    public Result postProcessEnter(PsiFile file, Editor editor, DataContext dataContext) {
        if (shouldAutoPopupAfterEnter(editor, file)) {
            WorkflowCompletionTypedAutoPopup.scheduleWorkflowPopup(file.getProject(), editor, file);
        }
        return Result.Continue;
    }

    public static boolean shouldAutoPopupAfterEnter(@Nullable Editor editor, @Nullable PsiFile file) {
        if (editor == null || file == null || getWorkflowFile(file).isEmpty()) {
            return false;
        }
        String textBeforeCaret = editor.getDocument()
            .getImmutableCharSequence()
            .subSequence(0, Math.min(editor.getCaretModel().getOffset(), editor.getDocument().getTextLength()))
            .toString();
        int currentLineStart = textBeforeCaret.lastIndexOf('\n');
        if (currentLineStart <= 0) {
            return false;
        }
        int previousLineStart = textBeforeCaret.lastIndexOf('\n', currentLineStart - 1) + 1;
        String previousLine = textBeforeCaret.substring(previousLineStart, currentLineStart).trim();
        return !previousLine.startsWith("#") && previousLine.endsWith(":");
    }
}
