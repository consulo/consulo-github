/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;

import java.util.List;

@ExtensionImpl
public class WorkflowSyntaxSchema implements JsonSchemaProviderFactory, DumbAware {
    @Override
    public List<JsonSchemaFileProvider> getProviders(Project project) {
        return WorkflowSyntax.schemaFileProviders();
    }
}
