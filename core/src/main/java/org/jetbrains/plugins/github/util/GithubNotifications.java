// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.util;

import consulo.component.ProcessCanceledException;
import consulo.configurable.StandardConfigurableIds;
import consulo.github.localize.GithubLocalize;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.ui.MessageBoxes;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.versionControlSystem.VcsNotifier;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jspecify.annotations.Nullable;

import static org.jetbrains.plugins.github.util.GithubUtil.getErrorTextFromException;

public final class GithubNotifications {
    private static final Logger LOG = GithubUtil.LOG;

    private GithubNotifications() {
    }

    private static boolean isOperationCanceled(Throwable e) {
        return e instanceof GithubOperationCanceledException ||
            e instanceof ProcessCanceledException;
    }

    public static void showInfo(Project project, LocalizeValue title, LocalizeValue message) {
        LOG.info(title + "; " + message);
        NotificationService.getInstance().newInfo(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION)
            .title(title)
            .content(message)
            .notify(project);
    }

    public static void showWarning(Project project, LocalizeValue title, LocalizeValue message) {
        LOG.info(title + "; " + message);
        NotificationService.getInstance().newWarn(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION)
            .title(title)
            .content(message)
            .notify(project);
    }

    public static void showError(Project project, LocalizeValue title, LocalizeValue message) {
        LOG.info(title + "; " + message);
        NotificationService.getInstance().newError(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION)
            .title(title)
            .content(message)
            .notify(project);
    }

    public static void showError(Project project, LocalizeValue title, Throwable e) {
        LOG.warn(title + "; ", e);
        if (isOperationCanceled(e)) {
            return;
        }
        NotificationService.getInstance().newError(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION)
            .title(title)
            .content(LocalizeValue.of(getErrorTextFromException(e)))
            .notify(project);
    }

    public static void showInfoURL(Project project, LocalizeValue title, String message, String url) {
        LOG.info(title + "; " + message + "; " + url);
        NotificationService.getInstance().newInfo(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION)
            .title(title)
            .content(LocalizeValue.of("<a href='" + url + "'>" + message + "</a>"))
            .hyperlinkListener(NotificationListener.URL_OPENING_LISTENER)
            .notify(project);
    }

    public static void showWarningURL(Project project,
                                      LocalizeValue title,
                                      String prefix,
                                      String highlight,
                                      String postfix,
                                      String url) {
        LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
        NotificationService.getInstance().newWarn(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION)
            .title(title)
            .content(LocalizeValue.of(prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix))
            .hyperlinkListener(NotificationListener.URL_OPENING_LISTENER)
            .notify(project);
    }

    @RequiredUIAccess
    public static void showErrorDialog(@Nullable Project project, LocalizeValue title, LocalizeValue message) {
        LOG.info(title + "; " + message);
        MessageBoxes.okError(message).title(title).showAsync(project);
    }

    public static AnAction getConfigureAction(Project project) {
        return DumbAwareAction.create(GithubLocalize.actionNotificationactionGithubnotificationsTextConfigure(),
            e -> ShowSettingsUtil.getInstance().showSettingsDialog(project, StandardConfigurableIds.ACCOUNTS_AND_PASSWORDS_GROUP, ""));
    }
}
