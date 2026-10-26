/**
 * @author VISTALL
 * @since 2023-04-29
 */
open module org.jetbrains.plugins.github.core {
    requires consulo.application.api;
    requires consulo.base.icon.library;
    requires consulo.code.editor.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.credential.storage.api;
    requires consulo.datacontext.api;
    requires consulo.document.api;
    requires consulo.http.api;
    requires consulo.diff.api;
    requires consulo.account.management.api;
    requires consulo.review.api;
    requires consulo.version.control.system.review.api;
    requires consulo.language.api;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.platform.api;
    requires consulo.project.api;
    requires consulo.proxy;
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
    requires consulo.file.chooser.api;
    requires consulo.ide.api;

    requires com.intellij.git;

    requires com.google.common;

    requires tools.jackson.core;
    requires tools.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    // TODO [VISTALL] remove in future
    requires java.desktop;

    exports consulo.github.icon;
    exports consulo.github.localize;
    exports org.jetbrains.plugins.github.api;
    exports org.jetbrains.plugins.github.api.data;
    exports org.jetbrains.plugins.github.api.data.request;
    exports org.jetbrains.plugins.github.api.util;
    exports org.jetbrains.plugins.github.authentication.accounts;
    exports org.jetbrains.plugins.github.exceptions;
    exports org.jetbrains.plugins.github.issue;
    exports org.jetbrains.plugins.github.ui;
    exports org.jetbrains.plugins.github.util;
}
