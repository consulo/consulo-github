/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

public enum NodeIcon {
    ICON_NODE(PlatformIconGroup.nodesInterface()),
    ICON_NEEDS(PlatformIconGroup.hierarchySupertypes()),
    ICON_ENV(PlatformIconGroup.nodesConstant()),
    ICON_RUNNER(PlatformIconGroup.nodesVariable()),
    ICON_STEP(PlatformIconGroup.nodesClass()),
    ICON_JOB(PlatformIconGroup.nodesClass()),
    ICON_OUTPUT(PlatformIconGroup.nodesVariable()),
    ICON_INPUT(PlatformIconGroup.nodesParameter()),
    ICON_SECRET_WORKFLOW(PlatformIconGroup.nodesStatic()),
    ICON_ENV_ROOT(PlatformIconGroup.nodesConstant()),
    ICON_ENV_JOB(PlatformIconGroup.nodesParameter()),
    ICON_ENV_STEP(PlatformIconGroup.nodesParameter()),
    ICON_TEXT_VARIABLE(PlatformIconGroup.nodesField()),

    RELOAD(PlatformIconGroup.actionsRefresh()),
    SETTINGS(PlatformIconGroup.generalSettings()),
    HYPERLINK(PlatformIconGroup.ideExternallink()),
    JUMP_TO_IMPLEMENTATION(PlatformIconGroup.gutterImplementedmethod()),
    SUPPRESS_ON(PlatformIconGroup.actionsCancel()),
    SUPPRESS_OFF(PlatformIconGroup.actionsOfflinemode()),
    IGNORED(PlatformIconGroup.actionsRegex()),
    EMPTY(PlatformIconGroup.nodesNodeplaceholder()),
    ;

    private final Image myIcon;

    NodeIcon(Image icon) {
        myIcon = icon;
    }

    public Image icon() {
        return myIcon;
    }
}
