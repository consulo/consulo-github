/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.NodeIcon;
import consulo.application.Application;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_IF;

public class WorkflowYaml {
    private static final String COMPLETION_DUMMY = "IntellijIdeaRulezzz";

    private WorkflowYaml() {
    }

    public static Optional<String[]> getCaretBracketItem(PsiElement position, int offset, String[] prefix) {
        PsiElement context = completionContextElement(position, offset);
        String rawText = context.getText();
        TextRange contextRange = context.getTextRange();
        if (rawText == null || contextRange == null) {
            return Optional.empty();
        }
        int rawCursorRel = offset - contextRange.getStartOffset();
        int dummyIndex = rawText.indexOf(COMPLETION_DUMMY);
        String wholeText = rawText.replace(COMPLETION_DUMMY, "");
        int adjustedCursorRel = dummyIndex >= 0 && dummyIndex < rawCursorRel
            ? rawCursorRel - COMPLETION_DUMMY.length()
            : rawCursorRel;
        int cursorRel = Math.max(0, Math.min(adjustedCursorRel, wholeText.length()));
        String offsetText = wholeText.substring(0, cursorRel);
        int bracketStart = offsetText.lastIndexOf("${{");
        if (cursorRel > 2 && isInBrackets(offsetText, bracketStart) || WorkflowPsi.getParent(context, FIELD_IF).isPresent()) {
            return getCaretBracketItem(prefix, wholeText, cursorRel);
        }
        return Optional.empty();
    }

    private static PsiElement completionContextElement(PsiElement position, int offset) {
        PsiElement current = position;
        PsiElement fallback = position;
        while (current != null && current.getParent() != current) {
            TextRange range = current.getTextRange();
            boolean containsOffset = range != null
                && range.getStartOffset() <= offset
                && offset <= range.getEndOffset();
            String text = current.getText();
            if (containsOffset && text != null && text.contains(COMPLETION_DUMMY)) {
                fallback = current;
                if (WorkflowPsi.isTextElement(current) || current instanceof YAMLScalar) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return fallback;
    }

    public static Optional<String[]> getCaretBracketItem(String[] prefix, String wholeText, int cursorRel) {
        Optional<String[]> pathItems = getPathCompletionItems(prefix, wholeText, cursorRel);
        if (pathItems.isPresent()) {
            return pathItems;
        }
        char previousChar = cursorRel == 0 ? ' ' : wholeText.charAt(cursorRel - 1);
        if (cursorRel > 1 && previousChar == '.') {
            int indexStart = getStartIndex(wholeText, cursorRel - 1);
            int indexEnd = getEndIndex(wholeText, cursorRel - 1, wholeText.length());
            return Optional.of(wholeText.substring(indexStart, indexEnd + 1).split("\\."));
        }
        else if (isNonValidNodeChar(previousChar)) {
            return Optional.of(prefix);
        }
        else {
            int indexStart = cursorRel == 0 ? 0 : getStartIndex(wholeText, cursorRel - 1);
            String[] prefArray = wholeText.substring(indexStart, cursorRel).split("\\.", -1);
            prefix[0] = prefArray[prefArray.length - 1];
            return Optional.of(wholeText.substring(indexStart, cursorRel - prefix[0].length()).split("\\."));
        }
    }

    private static Optional<String[]> getPathCompletionItems(String[] prefix, String wholeText, int cursorRel) {
        int pathStart = getPathStartIndex(wholeText, cursorRel);
        if (pathStart >= cursorRel) {
            return Optional.empty();
        }
        String path = wholeText.substring(pathStart, cursorRel);
        if (!path.contains(".") && !path.contains("[")) {
            return Optional.empty();
        }
        CaretPath caretPath = parseCaretPath(path);
        if (caretPath.items().length == 0) {
            return Optional.empty();
        }
        prefix[0] = caretPath.prefix();
        return Optional.of(caretPath.items());
    }

    private static int getPathStartIndex(String text, int cursorRel) {
        int result = Math.min(cursorRel, text.length());
        while (result > 0 && isPathChar(text.charAt(result - 1))) {
            result--;
        }
        return result;
    }

    private static CaretPath parseCaretPath(String text) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inBracket = false;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inBracket) {
                if (quote != 0 && character == quote) {
                    quote = 0;
                }
                else if (quote == 0 && (character == '\'' || character == '"')) {
                    quote = character;
                }
                else if (quote == 0 && character == ']') {
                    addPathItem(items, current);
                    inBracket = false;
                }
                else {
                    current.append(character);
                }
            }
            else if (character == '.') {
                addPathItem(items, current);
            }
            else if (character == '[') {
                addPathItem(items, current);
                inBracket = true;
            }
            else if (character != '\'' && character != '"') {
                current.append(character);
            }
        }
        return new CaretPath(items.toArray(String[]::new), current.toString());
    }

    private static void addPathItem(List<String> items, StringBuilder current) {
        if (!current.isEmpty()) {
            items.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isPathChar(char character) {
        return Character.isLetterOrDigit(character)
            || character == '_'
            || character == '-'
            || character == '.'
            || character == '['
            || character == ']'
            || character == '\''
            || character == '"';
    }

    private record CaretPath(String[] items, String prefix) {
    }

    public static int getStartIndex(CharSequence currentText, int fromIndex) {
        int result = fromIndex;
        while (result > 0) {
            char c = currentText.charAt(result);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') {
                result = result != fromIndex ? result + 1 : result;
                break;
            }
            result--;
        }
        return Math.min(result, fromIndex);
    }

    private static boolean isNonValidNodeChar(char c) {
        return !Character.isLetterOrDigit(c) && c != '_' && c != '-';
    }

    private static int getEndIndex(CharSequence currentText, int fromIndex, int toIndex) {
        int result = fromIndex;
        int endIndex = currentText.length();
        while (result < endIndex && result < toIndex) {
            if (isNonValidNodeChar(currentText.charAt(result))) {
                break;
            }
            result++;
        }
        return result;
    }

    private static boolean isInBrackets(String partString, int bracketStart) {
        return bracketStart != -1 && partString.lastIndexOf("}}") <= bracketStart;
    }

    public static LookupElement toLookupElement(NodeIcon icon, char suffix, String key, @Nullable String text) {
        LookupElementBuilder result = LookupElementBuilder
            .create(key)
            .withIcon(icon.icon())
            .withBoldness(icon != NodeIcon.ICON_ENV)
            .withTypeText(text)
            .withCaseSensitivity(false)
            .withInsertHandler((ctx, item) -> addSuffix(ctx, item, suffix));
        return PrioritizedLookupElement.withPriority(result, icon.ordinal() + 5d);
    }

    private static void addSuffix(InsertionContext ctx, LookupElement item, char suffix) {
        if (suffix != Character.MIN_VALUE) {
            String key = item.getLookupString();
            int startOffset = ctx.getStartOffset();
            Document document = ctx.getDocument();
            CharSequence documentChars = document.getCharsSequence();
            int tailOffset = ctx.getTailOffset();
            String insertText = suffixText(suffix, documentChars, tailOffset);

            document.replaceString(startOffset, suffixEndIndex(ctx, suffix, documentChars, tailOffset), key + insertText);
            ctx.getEditor().getCaretModel().moveToOffset(startOffset + (key + insertText).length());

            if (suffix == '.') {
                AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(ctx.getEditor());
            }
        }
    }

    private static int suffixEndIndex(InsertionContext ctx, char suffix, CharSequence documentChars, int tailOffset) {
        int result = tailOffset;
        if (ctx.getCompletionChar() == '\t') {
            while (result < documentChars.length()
                && documentChars.charAt(result) != suffix
                && !isLineBreak(documentChars.charAt(result))
            ) {
                result++;
            }
        }
        return result;
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r';
    }

    private static String suffixText(char suffix, CharSequence documentChars, int tailOffset) {
        StringBuilder result = new StringBuilder().append(suffix);
        boolean isNextCharSpace = tailOffset < documentChars.length() && documentChars.charAt(tailOffset) == ' ';
        if (suffix != '.' && !isNextCharSpace) {
            result.append(' ');
        }
        return result.toString();
    }

    public static Optional<Path> getWorkflowFile(@Nullable PsiElement psiElement) {
        return Optional.ofNullable(psiElement)
            .map(PsiElement::getContainingFile)
            .map(PsiFile::getOriginalFile)
            .map(PsiFile::getViewProvider)
            .map(FileViewProvider::getVirtualFile)
            .flatMap(WorkflowPsi::toPath)
            .filter(WorkflowYaml::isWorkflowPath);
    }

    public static boolean isWorkflowElement(@Nullable PsiElement psiElement) {
        if (psiElement == null || Application.get().isUnitTestMode()) {
            return psiElement != null;
        }
        PsiFile file = psiElement.getContainingFile();
        if (file == null) {
            return false;
        }
        VirtualFile virtualFile = file.getOriginalFile().getViewProvider().getVirtualFile();
        try {
            return isWorkflowPath(Path.of(virtualFile.getPath()));
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isWorkflowPath(@Nullable Path path) {
        return path != null && (isActionFile(path) || isWorkflowFile(path) || Application.get().isUnitTestMode());
    }

    public static boolean isYamlFile(Path path) {
        String fileName = path.getName(path.getNameCount() - 1).toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".yml") || fileName.endsWith(".yaml");
    }

    public static boolean isActionFile(Path path) {
        return path.getNameCount() > 1
            && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("action.yml")
            || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("action.yaml"));
    }

    public static boolean isWorkflowFile(@Nullable Path path) {
        return path != null && path.getNameCount() > 2
            && isYamlFile(path)
            && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflows")
            && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github");
    }

    /**
     * Checks whether a path is a GitHub Actions workflow file.
     *
     * @param path file path to inspect
     * @return true for YAML files under {@code .github/workflows}
     */
    public static boolean isGithubWorkflowFile(Path path) {
        return isWorkflowFile(path);
    }

    public static boolean isWorkflowTemplatePropertiesFile(Path path) {
        return path.getNameCount() > 2
            && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
            && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflow-templates")
            && isYamlFile(path);
    }

    public static boolean isIssueForms(Path path) {
        return path.getNameCount() > 2
            && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
            && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("ISSUE_TEMPLATE")
            && isYamlFile(path);
    }

    public static boolean isIssueConfigFile(Path path) {
        return path.getNameCount() > 2
            && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
            && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("ISSUE_TEMPLATE")
            && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("config.yml")
            || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("config.yaml"));
    }

    public static boolean isFoundingFile(Path path) {
        return path.getNameCount() > 1
            && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("FUNDING.yml")
            || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("FUNDING.yaml"));
    }

    public static boolean isDependabotFile(Path path) {
        return path.getNameCount() > 1
            && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase(".github")
            && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("dependabot.yml")
            || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("dependabot.yaml"));
    }

    public static boolean isDiscussionFile(Path path) {
        return path.getNameCount() > 2
            && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
            && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("DISCUSSION_TEMPLATE")
            && isYamlFile(path);
    }
}
