/**
 * @author VISTALL
 * @since 2026-10-26
 */
module org.jetbrains.plugins.github.workflow {
    requires org.jetbrains.plugins.github.core;

    requires org.jetbrains.plugins.yaml.api;

    requires consulo.json.api;
    requires consulo.json.scheme.api;
    requires consulo.json.scheme.impl;

    requires consulo.account.management.api;
    requires consulo.application.api;
    requires consulo.application.content.api;
    requires consulo.base.icon.library;
    requires consulo.code.editor.api;
    requires consulo.color.scheme.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.datacontext.api;
    requires consulo.disposer.api;
    requires consulo.document.api;
    requires consulo.file.chooser.api;
    requires consulo.file.editor.api;
    requires consulo.ide.api;
    requires consulo.language.api;
    requires consulo.language.editor.api;
    requires consulo.language.impl;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.module.content.api;
    requires consulo.platform.api;
    requires consulo.project.api;
    requires consulo.project.ui.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.util.concurrent.coroutine;
    requires consulo.util.dataholder;
    requires consulo.util.lang;
    requires consulo.util.xml.serializer;
    requires consulo.virtual.file.system.api;

    requires tools.jackson.databind;

    requires com.intellij.git;
    requires consulo.build.ui.api;
    requires consulo.container.api;
    requires consulo.execution.api;
    requires consulo.process.api;
    requires consulo.version.control.system.distributed.api;

    exports consulo.github.workflow.localize;

    opens com.github.yunabraska.githubworkflow.git to tools.jackson.databind;
    opens com.github.yunabraska.githubworkflow.state to consulo.util.xml.serializer;
    opens com.github.yunabraska.githubworkflow.run;
}
