/**
 * @author VISTALL
 * @since 29/04/2023
 */
open module org.jetbrains.plugins.github {
    requires consulo.ide.api;
    requires com.intellij.git;

    requires commons.httpclient;

    requires com.google.gson;

    // TODO [VISTALL] remove in future
    requires java.desktop;
    requires consulo.ide.impl;
    requires forms.rt;
}