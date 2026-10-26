/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.yunabraska.githubworkflow.run;

import com.dslplatform.json.CompiledJson;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import consulo.util.xml.serializer.annotation.Property;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
@CompiledJson
public class WorkflowRunConfigurationOptions extends RunConfigurationOptions {
    private String myServerUrl = GithubServerPath.DEFAULT_SERVER.toUrl();
    private String myOwner = "";
    private String myRepo = "";
    private String myWorkflowPath = "";
    private String myRef = "main";
    private Map<String, String> myInputs = new LinkedHashMap<>();

    @Attribute("serverUrl")
    public String getServerUrl() {
        return myServerUrl;
    }

    public void setServerUrl(@Nullable String serverUrl) {
        myServerUrl = clean(serverUrl);
    }

    @Attribute("owner")
    public String getOwner() {
        return myOwner;
    }

    public void setOwner(@Nullable String owner) {
        myOwner = clean(owner);
    }

    @Attribute("repo")
    public String getRepo() {
        return myRepo;
    }

    public void setRepo(@Nullable String repo) {
        myRepo = clean(repo);
    }

    @Attribute("workflowPath")
    public String getWorkflowPath() {
        return myWorkflowPath;
    }

    public void setWorkflowPath(@Nullable String workflowPath) {
        myWorkflowPath = clean(workflowPath);
    }

    @Attribute("ref")
    public String getRef() {
        return myRef;
    }

    public void setRef(@Nullable String ref) {
        myRef = clean(ref);
    }

    @Property(surroundWithTag = false)
    @MapAnnotation(
        surroundWithTag = false,
        surroundKeyWithTag = false,
        surroundValueWithTag = false,
        keyAttributeName = "name",
        valueAttributeName = "value",
        entryTagName = "input"
    )
    public Map<String, String> getInputs() {
        return myInputs;
    }

    public void setInputs(@Nullable Map<String, String> inputs) {
        myInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
