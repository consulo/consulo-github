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
module org.jetbrains.plugins.github.tracker {
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
    requires java.desktop;

    requires org.jetbrains.plugins.github.core;

    exports org.jetbrains.plugins.github.tasks;
}
