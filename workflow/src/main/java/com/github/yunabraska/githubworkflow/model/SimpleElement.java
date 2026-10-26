/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.lookup.LookupElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.removeQuotes;
import static java.util.Optional.ofNullable;

public record SimpleElement(@Nullable String key, String text, @Nullable TextRange range, @Nullable NodeIcon icon) {
    public SimpleElement(@Nullable String key, String text, @Nullable NodeIcon icon) {
        this(key, text, null, icon);
    }

    public SimpleElement(@Nullable String key, String text, @Nullable TextRange range) {
        this(key, text, range, null);
    }

    public SimpleElement(String text, @Nullable TextRange range) {
        this(null, text, range, null);
    }

    @Override
    public NodeIcon icon() {
        return icon != null ? icon : NodeIcon.ICON_NODE;
    }

    public String textNoQuotes() {
        return removeQuotes(text);
    }

    public int startIndexOffset() {
        return ofNullable(range).map(TextRange::getStartOffset).orElse(-1);
    }

    public int endIndexOffset() {
        return ofNullable(range).map(TextRange::getEndOffset).orElse(-1);
    }

    public LookupElement toLookupElement() {
        return WorkflowYaml.toLookupElement(icon(), Character.MIN_VALUE, ofNullable(key).orElse(""), text);
    }

    public static List<SimpleElement> completionItemsOf(@Nullable Map<String, String> map, NodeIcon icon) {
        return map == null ? new ArrayList<>() : map.entrySet().stream()
            .map(item -> completionItemOf(item.getKey(), item.getValue(), icon))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static @Nullable SimpleElement completionItemOf(@Nullable String key, @Nullable String text, NodeIcon icon) {
        return key == null ? null : new SimpleElement(key, orEmpty(text), icon);
    }

    private static String orEmpty(@Nullable String text) {
        return ofNullable(text).orElse("");
    }
}
