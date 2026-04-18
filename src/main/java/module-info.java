/**
 * @author VISTALL
 * @since 2023-04-29
 */
open module org.jetbrains.plugins.github {
    requires consulo.application.api;
    requires consulo.base.icon.library;
    requires consulo.code.editor.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.credential.storage.api;
    requires consulo.datacontext.api;
    requires consulo.document.api;
    requires consulo.http.api;
    requires consulo.language.api;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.platform.api;
    requires consulo.project.api;
    requires consulo.project.ui.api;
    requires consulo.task.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.ui.ex.awt.api;
    requires consulo.util.collection;
    requires consulo.util.dataholder;
    requires consulo.util.lang;
    requires consulo.util.xml.serializer;
    requires consulo.version.control.system.api;
    requires consulo.version.control.system.distributed.api;
    requires consulo.version.control.system.log.api;
    requires consulo.virtual.file.system.api;

    requires com.intellij.git;

    requires commons.httpclient;

    requires com.google.gson;

    // TODO [VISTALL] remove in future
    requires java.desktop;
    requires forms.rt;
}
