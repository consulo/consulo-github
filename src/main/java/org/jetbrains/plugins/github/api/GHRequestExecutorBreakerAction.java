// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;

@ActionImpl(id = "Github.Break.Api.Requests", parents = @ActionParentRef(@ActionRef(id = "Internal")))
public class GHRequestExecutorBreakerAction extends ToggleAction implements DumbAware {
    public GHRequestExecutorBreakerAction() {
        super(LocalizeValue.localizeTODO("Break GitHub API Requests"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return GHRequestExecutorBreaker.getInstance().isRequestsShouldFail();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        GHRequestExecutorBreaker.getInstance().setRequestsShouldFail(state);
    }
}
