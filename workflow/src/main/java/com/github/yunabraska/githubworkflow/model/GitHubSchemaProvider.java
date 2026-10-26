/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class GitHubSchemaProvider implements JsonSchemaFileProvider {
    private static final String SCHEMA_ROOT = "/github-workflow/schemas/";

    private final String mySchemaName;
    private final String myDisplayName;
    private final Predicate<Path> myValidatePath;

    public GitHubSchemaProvider(String schemaName, String displayName, Predicate<Path> validatePath) {
        mySchemaName = schemaName;
        myDisplayName = displayName;
        myValidatePath = validatePath;
    }

    @Override
    public String getName() {
        return GitHubWorkflowLocalize.schemaAuto(myDisplayName).get();
    }

    @Override
    public boolean isAvailable(VirtualFile file) {
        return Optional.of(file).flatMap(WorkflowPsi::toPath).filter(myValidatePath).isPresent();
    }

    @Override
    public @Nullable VirtualFile getSchemaFile() {
        return JsonSchemaProviderFactory.getResourceFile(GitHubSchemaProvider.class, SCHEMA_ROOT + mySchemaName + ".json");
    }

    @Override
    public SchemaType getSchemaType() {
        return SchemaType.schema;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GitHubSchemaProvider that = (GitHubSchemaProvider) o;
        return Objects.equals(mySchemaName, that.mySchemaName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mySchemaName);
    }
}
