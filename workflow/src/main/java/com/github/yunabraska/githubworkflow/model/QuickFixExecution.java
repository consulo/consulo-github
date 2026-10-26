/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public record QuickFixExecution(SyntaxAnnotation syntaxAnnotation, Project project, @Nullable Editor editor, @Nullable PsiFile file) {
}
