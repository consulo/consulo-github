/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.IconRenderer;
import com.github.yunabraska.githubworkflow.model.LocalActionReferenceResolver;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.impl.psi.path.WebReference;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.IGNORED;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.JUMP_TO_IMPLEMENTATION;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_OFF;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.triggerSyntaxHighlightingForActiveFiles;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.addAnnotation;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.deleteElementAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.deleteInvalidAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.newJumpToFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.newLoginAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.newReloadAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.newUnresolvedAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.replaceAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getTextElement;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.goToDeclarationString;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.toYAMLKeyValue;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowReferences.ACTION_KEY;
import static java.util.Optional.ofNullable;

public class Action {
    public static void highLightAction(AnnotationHolder holder, @Nullable YAMLKeyValue element) {
        if (element == null) {
            return;
        }
        List<SyntaxAnnotation> result = new ArrayList<>();
        GitHubAction action = GitHubActionCache.getAction(element);
        if (action == null) {
            result.add(unresolvedAction(element));
        }
        else {
            if (action.isResolved() && !action.isLocal()) {
                result.add(newReloadAction(action));
                newerMajorActionRef(element, action).ifPresent(result::add);
            }
            result.add(newSuppressAction(action));
            highlightResolvedActionReference(holder, element, action, result);
            if (!action.isResolved() && !action.isSuppressed()) {
                result.add(action.isLocal() ? deleteInvalidAction(element) : unresolvedAction(element));
            }
        }
        addAnnotation(holder, element, result);
    }

    private static SyntaxAnnotation unresolvedAction(YAMLKeyValue element) {
        String usesValue = WorkflowPsi.removeQuotes(ofNullable(element.getValueText()).orElse("").trim());
        return RemoteActionProviders.hasAccountFor(usesValue) || isLocalUses(usesValue)
            ? newUnresolvedAction(element)
            : newLoginAction(element);
    }

    private static boolean isLocalUses(@Nullable String usesValue) {
        String normalized = ofNullable(usesValue).orElse("");
        return normalized.startsWith(".") || normalized.startsWith("/") || normalized.startsWith("docker://");
    }

    public static void highlightActionInput(AnnotationHolder holder, PsiElement psiElement) {
        toYAMLKeyValue(psiElement)
            .filter(withItem -> getParent(withItem.getParent(), FIELD_WITH).isPresent())
            .ifPresent(withItem -> highlightCallableParameter(holder, withItem, FIELD_WITH));
        toYAMLKeyValue(psiElement)
            .filter(secretItem -> getParent(secretItem.getParent(), FIELD_SECRETS).filter(secrets -> getParent(secrets, FIELD_ON).isEmpty()).isPresent())
            .ifPresent(secretItem -> highlightCallableParameter(holder, secretItem, FIELD_SECRETS));
    }

    private static void highlightCallableParameter(AnnotationHolder holder, YAMLKeyValue item, String parameterType) {
        getAction(getParentStepOrJob(item).orElse(null))
            .filter(GitHubAction::isResolved)
            .ifPresent(action -> {
                Set<String> validIds = FIELD_SECRETS.equals(parameterType) ? action.freshSecrets().keySet() : action.freshInputs().keySet();
                String id = item.getKeyText();
                if (FIELD_WITH.equals(parameterType)) {
                    newSuppressInput(action, id).createAnnotation(item, ofNullable(item.getKey()).map(PsiElement::getTextRange).orElseGet(item::getTextRange), holder);
                }
                if (!validIds.contains(id)) {
                    LocalizeValue label = FIELD_SECRETS.equals(parameterType)
                        ? GitHubWorkflowLocalize.inspectionParameterSecret()
                        : GitHubWorkflowLocalize.inspectionParameterInput();
                    addAnnotation(holder, item, new SyntaxAnnotation(
                        GitHubWorkflowLocalize.inspectionActionDeleteInvalid(label, id),
                        SUPPRESS_ON,
                        deleteElementAction(item.getTextRange())
                    ));
                }
            });
    }

    public static List<SyntaxAnnotation> highlightActionOutputs(YAMLSequenceItem stepItem, SimpleElement part) {
        return getAction(stepItem).map(action -> newSuppressOutput(action, part.text())).map(List::of).orElseGet(Collections::emptyList);
    }

    public static void highlightActionOutputs(AnnotationHolder holder, PsiElement psiElement, @Nullable YAMLSequenceItem stepItem, SimpleElement part) {
        getAction(stepItem).ifPresent(action -> {
            String outputId = part.text();
            int startRange = psiElement.getTextRange().getStartOffset();
            if (!action.freshOutputs(false).containsKey(outputId) && part.range() != null) {
                newSuppressOutput(action, outputId).createAnnotation(psiElement, new TextRange(startRange + part.range().getStartOffset(), startRange + part.range().getEndOffset()), holder);
            }
        });
    }

    public static Optional<PsiReference[]> referenceGithubAction(PsiElement psiElement) {
        return getParent(psiElement, FIELD_USES)
            .map(GitHubActionCache::getAction)
            .filter(GitHubAction::isResolved)
            .filter(action -> !action.isSuppressed())
            .map(action -> {
                psiElement.putUserData(ACTION_KEY, action);
                return action.isLocal() ? new PsiReference[]{new LocalActionReferenceResolver(psiElement)} : new PsiReference[]{new WebReference(psiElement, action.githubUrl())};
            });
    }

    private static void highlightResolvedActionReference(AnnotationHolder holder, YAMLKeyValue element, GitHubAction action, List<SyntaxAnnotation> result) {
        if (action.isResolved() && !action.isSuppressed()) {
            String tooltip = goToDeclarationString();
            getTextElement(element).ifPresent(textElement -> {
                AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                    .range(textElement)
                    .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                    .tooltip(tooltip);
                if (action.isLocal()) {
                    SyntaxAnnotation jumpToFile = newJumpToFile(action);
                    result.add(jumpToFile);
                    annotation = annotation.gutterIconRenderer(new IconRenderer(jumpToFile, element, JUMP_TO_IMPLEMENTATION));
                }
                annotation.create();
            });
        }
    }

    private static Optional<SyntaxAnnotation> newerMajorActionRef(YAMLKeyValue element, GitHubAction action) {
        String usesValue = action.usesValue();
        int separator = usesValue.lastIndexOf('@');
        if (separator < 1 || separator == usesValue.length() - 1 || action.remoteRefs().isEmpty()) {
            return Optional.empty();
        }
        String currentRef = usesValue.substring(separator + 1);
        Optional<Integer> currentMajor = majorRef(currentRef);
        if (currentMajor.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> latestRef = action.remoteRefs().stream()
            .filter(ref -> majorRef(ref).map(major -> major > currentMajor.get()).orElse(false))
            .max((left, right) -> Integer.compare(majorRef(left).orElse(0), majorRef(right).orElse(0)));
        if (latestRef.isEmpty()) {
            return Optional.empty();
        }
        String newUsesValue = usesValue.substring(0, separator + 1) + latestRef.get();
        TextRange range = getTextElement(element).map(PsiElement::getTextRange).orElseGet(element::getTextRange);
        return Optional.of(new SyntaxAnnotation(
            GitHubWorkflowLocalize.inspectionActionUpdateMajor(usesValue, newUsesValue),
            RELOAD,
            HighlightSeverity.WEAK_WARNING,
            ProblemHighlightType.WEAK_WARNING,
            replaceAction(range, newUsesValue)
        ));
    }

    private static Optional<Integer> majorRef(@Nullable String ref) {
        String normalized = ofNullable(ref).orElse("").trim();
        String digits = normalized.startsWith("v") || normalized.startsWith("V")
            ? normalized.substring(1)
            : normalized;
        return digits.matches("\\d+")
            ? Optional.of(Integer.parseInt(digits))
            : Optional.empty();
    }

    public static List<SimpleElement> listActionsOutputs(@Nullable PsiElement psiElement) {
        return getAction(psiElement)
            .map(GitHubAction::freshOutputs)
            .map(map -> completionItemsOf(map, ICON_OUTPUT))
            .orElseGet(Collections::emptyList);
    }

    private static Optional<GitHubAction> getAction(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement)
            .flatMap(element -> getChild(element, FIELD_USES))
            .map(GitHubActionCache::getAction);
    }

    private static SyntaxAnnotation newSuppressAction(GitHubAction action) {
        boolean suppressed = action.isSuppressed();
        return new SyntaxAnnotation(
            toggleText(action.name(), suppressed),
            suppressed ? SUPPRESS_OFF : SUPPRESS_ON,
            HighlightSeverity.INFORMATION,
            suppressed ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION,
            f -> {
                action.isSuppressed(!suppressed);
                triggerSyntaxHighlightingForActiveFiles();
            }
        );
    }

    private static SyntaxAnnotation newSuppressInput(GitHubAction action, String id) {
        boolean suppressed = action.ignoredInputs().contains(id);
        return new SyntaxAnnotation(
            toggleText(id, suppressed),
            suppressed ? IGNORED : SUPPRESS_ON,
            HighlightSeverity.INFORMATION,
            suppressed ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION,
            f -> {
                action.suppressInput(id, !suppressed);
                triggerSyntaxHighlightingForActiveFiles();
            }
        );
    }

    private static SyntaxAnnotation newSuppressOutput(GitHubAction action, String id) {
        boolean suppressed = action.ignoredOutputs().contains(id);
        HighlightSeverity level = action.freshOutputs().containsKey(id) ? HighlightSeverity.INFORMATION : HighlightSeverity.ERROR;
        return new SyntaxAnnotation(
            toggleText(id, suppressed),
            suppressed ? IGNORED : SUPPRESS_ON,
            level,
            suppressed ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION,
            f -> {
                action.suppressOutput(id, !suppressed);
                triggerSyntaxHighlightingForActiveFiles();
            }
        );
    }

    private static LocalizeValue toggleText(String id, boolean suppressed) {
        return GitHubWorkflowLocalize.inspectionWarningToggle(suppressed ? GitHubWorkflowLocalize.inspectionWarningOn() : GitHubWorkflowLocalize.inspectionWarningOff(), id);
    }

    private Action() {
    }
}
