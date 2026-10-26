/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.QuickFixExecution;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.configurable.StandardConfigurableIds;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNavigationSupport;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.JUMP_TO_IMPLEMENTATION;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SETTINGS;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_CONCLUSION;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTCOME;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.removeQuotes;
import static java.util.Optional.ofNullable;

public class WorkflowAnnotations {
    public static final List<String> VALID_OUTPUT_FIELDS = List.of(FIELD_OUTPUTS);
    public static final List<String> VALID_STEP_FIELDS = List.of(FIELD_OUTPUTS, FIELD_CONCLUSION, FIELD_OUTCOME);

    private WorkflowAnnotations() {
    }

    public static void addAnnotation(@Nullable AnnotationHolder holder, @Nullable PsiElement element, SyntaxAnnotation result) {
        addAnnotation(holder, element, List.of(result));
    }

    public static void addAnnotation(@Nullable AnnotationHolder holder, @Nullable PsiElement element, @Nullable List<SyntaxAnnotation> result) {
        if (holder != null && element != null && result != null && !result.isEmpty()) {
            createAnnotation(element, element.getTextRange(), holder, result);
        }
    }

    public static void ifEnoughItems(
        AnnotationHolder holder,
        PsiElement psiElement,
        SimpleElement[] parts,
        int min,
        int max,
        Consumer<SimpleElement> then
    ) {
        if (parts.length < min || parts.length < 2) {
            TextRange range = psiElement.getTextRange();
            new SyntaxAnnotation(
                GitHubWorkflowLocalize.inspectionStatementIncomplete(Arrays.stream(parts).map(SimpleElement::text).collect(Collectors.joining("."))),
                null,
                null
            ).createAnnotation(psiElement, new TextRange(range.getStartOffset() + parts[0].startIndexOffset(), range.getStartOffset() + parts[parts.length - 1].endIndexOffset()), holder);
        }
        else if (max != -1 && parts.length > max) {
            SimpleElement[] tooLongPart = Arrays.copyOfRange(parts, max, parts.length);
            TextRange textRange = textRangeIncludingPreviousDot(psiElement, tooLongPart[0], tooLongPart[tooLongPart.length - 1]);
            new SyntaxAnnotation(
                GitHubWorkflowLocalize.inspectionInvalidSuffixRemove(Arrays.stream(tooLongPart).map(SimpleElement::text).collect(Collectors.joining("."))),
                SUPPRESS_ON,
                deleteElementAction(textRange)
            ).createAnnotation(psiElement, textRange, holder);
        }
        else {
            then.accept(parts[1]);
        }
    }

    public static boolean isDefinedItem0(PsiElement psiElement, AnnotationHolder holder, SimpleElement itemId, Collection<String> items) {
        if (isEmpty(items, itemId, psiElement, holder)) {
            return false;
        }
        else if (!items.contains(itemId.text())) {
            TextRange textRange = simpleTextRange(psiElement, itemId);
            createAnnotation(psiElement, textRange, holder, items.stream().map(item -> new SyntaxAnnotation(
                GitHubWorkflowLocalize.inspectionReplaceWith(item),
                RELOAD,
                replaceAction(textRange, item)
            )).toList());
            return false;
        }
        return true;
    }

    public static boolean isField2Valid(PsiElement psiElement, AnnotationHolder holder, SimpleElement itemId) {
        return isField2Valid(psiElement, holder, itemId, VALID_OUTPUT_FIELDS);
    }

    public static boolean isField2Valid(PsiElement psiElement, AnnotationHolder holder, SimpleElement itemId, List<String> validFields) {
        if (!validFields.contains(itemId.text())) {
            TextRange textRange = textRangeIncludingPreviousDot(psiElement, itemId, itemId);
            new SyntaxAnnotation(
                GitHubWorkflowLocalize.inspectionInvalidRemove(itemId.text()),
                SUPPRESS_ON,
                deleteElementAction(textRange)
            ).createAnnotation(psiElement, textRange, holder);
            return false;
        }
        return true;
    }

    public static void isValidItem3(PsiElement psiElement, AnnotationHolder holder, @Nullable SimpleElement itemId, List<String> outputs) {
        if (itemId != null && !isEmpty(outputs, itemId, psiElement, holder) && !outputs.contains(itemId.text())) {
            TextRange textRange = simpleTextRange(psiElement, itemId);
            createAnnotation(psiElement, textRange, holder, outputs.stream().filter(WorkflowPsi::hasText).map(item -> new SyntaxAnnotation(
                GitHubWorkflowLocalize.inspectionReplaceWith(item),
                RELOAD,
                replaceAction(textRange, item)
            )).toList());
        }
    }

    public static SyntaxAnnotation newReloadAction(GitHubAction action) {
        return new SyntaxAnnotation(
            GitHubWorkflowLocalize.inspectionActionReload(action.name()),
            RELOAD,
            HighlightSeverity.INFORMATION,
            ProblemHighlightType.INFORMATION,
            f -> GitHubActionCache.reloadActionAsync(f.project(), action.usesValue())
        );
    }

    public static SyntaxAnnotation newUnresolvedAction(YAMLKeyValue element) {
        return new SyntaxAnnotation(
            GitHubWorkflowLocalize.inspectionActionUnresolved(removeQuotes(element.getValueText())),
            SETTINGS,
            HighlightSeverity.WEAK_WARNING,
            ProblemHighlightType.WEAK_WARNING,
            f -> {
                ShowSettingsUtil.getInstance().showSettingsDialog(f.project(), StandardConfigurableIds.ACCOUNTS_AND_PASSWORDS_GROUP, "");
                resolveAction(element);
            }
        );
    }

    public static SyntaxAnnotation newLoginAction(YAMLKeyValue element) {
        return new SyntaxAnnotation(
            GitHubWorkflowLocalize.inspectionActionLogin(removeQuotes(element.getValueText())),
            SETTINGS,
            HighlightSeverity.WEAK_WARNING,
            ProblemHighlightType.WEAK_WARNING,
            f -> RemoteActionProviders.requestLogin(f.project()).whenComplete((account, error) -> {
                if (account != null) {
                    GitHubActionCache.getActionCache().resolveUnresolvedRemoteActions();
                }
            })
        );
    }

    public static SyntaxAnnotation deleteInvalidAction(YAMLKeyValue element) {
        TextRange textRange = ofNullable(element.getValue()).map(PsiElement::getTextRange).orElseGet(element::getTextRange);
        return new SyntaxAnnotation(
            GitHubWorkflowLocalize.inspectionInvalidRemove(element.getValueText()),
            SUPPRESS_ON,
            HighlightSeverity.WEAK_WARNING,
            ProblemHighlightType.WEAK_WARNING,
            deleteElementAction(textRange)
        );
    }

    public static SyntaxAnnotation newJumpToFile(GitHubAction action) {
        return new SyntaxAnnotation(
            GitHubWorkflowLocalize.inspectionActionJump(action.name()),
            JUMP_TO_IMPLEMENTATION,
            HighlightSeverity.INFORMATION,
            ProblemHighlightType.INFORMATION,
            f -> jumpToFile(action, f.project()),
            false
        );
    }

    public static void jumpToFile(GitHubAction action, @Nullable Project project) {
        if (project == null) {
            return;
        }
        action.getLocalPath(project)
            .map(path -> LocalFileSystem.getInstance().findFileByPath(path))
            .map(target -> PsiManager.getInstance(project).findFile(target))
            .ifPresent(psiFile -> PsiNavigationSupport.getInstance().createNavigatable(project, psiFile.getVirtualFile(), 0).navigate(true));
    }

    public static Consumer<QuickFixExecution> deleteElementAction(TextRange textRange) {
        return replaceAction(textRange, "");
    }

    public static Consumer<QuickFixExecution> replaceAction(TextRange textRange, String newValue) {
        return fix -> {
            PsiFile file = fix.file();
            if (file == null) {
                return;
            }
            PsiFile topLevelFile = InjectedLanguageManager.getInstance(fix.project()).getTopLevelFile(file);
            Document document = PsiDocumentManager.getInstance(fix.project()).getDocument(topLevelFile);
            if (document != null) {
                WriteCommandAction.runWriteCommandAction(fix.project(), () -> document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), newValue));
            }
        };
    }

    public static <T> @Nullable T getFirstChild(@Nullable List<T> children) {
        return children != null && !children.isEmpty() ? children.get(0) : null;
    }

    public static TextRange simpleTextRange(PsiElement psiElement, SimpleElement item) {
        TextRange textRange = psiElement.getTextRange();
        int startOffset = textRange.getStartOffset();
        return new TextRange(
            Math.max(startOffset + item.startIndexOffset(), startOffset),
            Math.min(startOffset + item.endIndexOffset(), textRange.getEndOffset())
        );
    }

    private static TextRange textRangeIncludingPreviousDot(PsiElement psiElement, SimpleElement firstItem, SimpleElement lastItem) {
        TextRange textRange = psiElement.getTextRange();
        int startOffset = textRange.getStartOffset();
        int localStart = firstItem.startIndexOffset();
        int start = localStart > 0 && psiElement.getText().charAt(localStart - 1) == '.'
            ? startOffset + localStart - 1
            : startOffset + localStart;
        return new TextRange(
            Math.max(start, startOffset),
            Math.min(startOffset + lastItem.endIndexOffset(), textRange.getEndOffset())
        );
    }

    private static boolean isEmpty(Collection<String> items, @Nullable SimpleElement itemId, PsiElement psiElement, AnnotationHolder holder) {
        if (itemId != null && items.isEmpty()) {
            TextRange textRange = simpleTextRange(psiElement, itemId);
            createAnnotation(psiElement, textRange, holder, List.of(new SyntaxAnnotation(
                GitHubWorkflowLocalize.inspectionInvalidRemove(itemId.text()),
                SUPPRESS_ON,
                deleteElementAction(textRange)
            )));
            return true;
        }
        return false;
    }

    private static void resolveAction(@Nullable YAMLKeyValue element) {
        if (element == null) {
            return;
        }
        Application.get().executeOnPooledThread(() -> {
            Optional<GitHubAction> unresolved = ReadAction.compute(() -> Optional.of(element)
                .filter(PsiElement::isValid)
                .flatMap(psiElement -> WorkflowPsi.getParent(psiElement, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> !action.isResolved()));
            unresolved.map(List::of).ifPresent(GitHubActionCache::resolveActionsAsync);
        });
    }
}
