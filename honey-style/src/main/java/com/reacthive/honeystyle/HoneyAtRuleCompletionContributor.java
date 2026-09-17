package com.reacthive.honeystyle;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completes honey-style at-rules inside a styled/css template literal.
 *
 * <pre>
 *   &#64;honey-media (sm:up) { ... }     breakpoints come from theme.breakpoints
 *   &#64;honey-stack (2) { ... }
 *   &#64;honey-center (horizontal) { ... }
 * </pre>
 *
 * <p>Registered for any language because WebStorm's styled-components support injects CSS into the
 * template, so the caret sits in an injected CSS file rather than the host TS file. The contributor
 * returns immediately unless the caret is inside a template literal and the text before it looks
 * like an at-rule, so it costs nothing elsewhere.
 */
public final class HoneyAtRuleCompletionContributor extends CompletionContributor {

    private static final Pattern AT_RULE_PREFIX = Pattern.compile("@([A-Za-z-]*)$");
    private static final Pattern RULE_PARAMS = Pattern.compile("@(honey-[a-z-]+)\\s*\\(([^)]*)$");
    private static final int LOOKBACK = 200;

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        Project project = position.getProject();
        if (!HoneyStyleSettings.getInstance(project).isEnabled()) {
            return;
        }
        String before = textBeforeCaret(parameters);
        if (before == null || before.indexOf('@') < 0 || !insideTemplateLiteral(project, position)) {
            return;
        }

        String[] params = ruleParams(before);
        if (params != null) {
            addParameters(project, result, params[0], params[1]);
            return;
        }
        String namePrefix = atRuleNamePrefix(before);
        if (namePrefix != null) {
            addRuleNames(result.withPrefixMatcher(namePrefix));
        }
    }

    /** @return the partial at-rule name typed after {@code @}, or null if the caret is elsewhere */
    static @Nullable String atRuleNamePrefix(String before) {
        Matcher matcher = AT_RULE_PREFIX.matcher(before);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** @return {rule name, text typed inside the parens}, or null when not inside an at-rule's parens */
    static String @Nullable [] ruleParams(String before) {
        Matcher matcher = RULE_PARAMS.matcher(before);
        return matcher.find() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    private static void addRuleNames(CompletionResultSet result) {
        for (HoneyAtRules.AtRule rule : HoneyAtRules.ALL) {
            LookupElementBuilder element = LookupElementBuilder.create(rule.name()).withBoldness(true);
            if (!rule.params().isEmpty()) {
                element = element.withTailText(" (" + rule.params() + ")", true);
            }
            result.addElement(rule.takesParams()
                    ? element.withInsertHandler(HoneyAtRuleCompletionContributor::insertParens)
                    : element);
        }
    }

    private static void addParameters(Project project,
                                      CompletionResultSet result,
                                      String ruleName,
                                      String typedParams) {
        String token = currentToken(typedParams);
        CompletionResultSet scoped = result.withPrefixMatcher(token);

        switch (ruleName) {
            case "honey-media" -> {
                for (HoneyBreakpoint breakpoint : HoneyThemeService.getInstance(project).getBreakpoints()) {
                    scoped.addElement(LookupElementBuilder.create(breakpoint.name()));
                    for (String direction : HoneyAtRules.BREAKPOINT_DIRECTIONS) {
                        scoped.addElement(LookupElementBuilder.create(breakpoint.name() + ":" + direction));
                    }
                }
                addAll(scoped, HoneyAtRules.MEDIA_ORIENTATIONS, "orientation");
                addAll(scoped, HoneyAtRules.MEDIA_TYPES, "media type");
            }
            case "honey-center" -> addAll(scoped, HoneyAtRules.CENTER_AXES, "axis");
            case "honey-if" -> addAll(scoped, HoneyAtRules.IF_VALUES, "condition");
            default -> {
            }
        }
    }

    private static void addAll(CompletionResultSet result, List<String> values, String typeText) {
        for (String value : values) {
            result.addElement(LookupElementBuilder.create(value).withTypeText(typeText));
        }
    }

    /** {@code @honey-media} takes several space-separated tokens; complete the one being typed. */
    static String currentToken(String typedParams) {
        int lastSpace = typedParams.lastIndexOf(' ');
        return lastSpace < 0 ? typedParams : typedParams.substring(lastSpace + 1);
    }

    private static void insertParens(InsertionContext context, LookupElement element) {
        Document document = context.getDocument();
        int tail = context.getTailOffset();
        if (document.getTextLength() > tail && document.getCharsSequence().charAt(tail) == '(') {
            return;
        }
        document.insertString(tail, " ()");
        context.getEditor().getCaretModel().moveToOffset(tail + 2);
    }

    private static @Nullable String textBeforeCaret(CompletionParameters parameters) {
        PsiFile file = parameters.getPosition().getContainingFile();
        if (file == null) {
            return null;
        }
        CharSequence text = file.getViewProvider().getContents();
        int offset = Math.min(parameters.getOffset(), text.length());
        if (offset <= 0) {
            return null;
        }
        return text.subSequence(Math.max(0, offset - LOOKBACK), offset).toString();
    }

    /**
     * True when the caret is in a JS template literal, directly or through a CSS injection.
     */
    private static boolean insideTemplateLiteral(Project project, PsiElement position) {
        if (PsiTreeUtil.getParentOfType(position, JSStringTemplateExpression.class, false) != null) {
            return true;
        }
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
        PsiElement host = manager.getInjectionHost(position);
        if (host == null) {
            PsiFile topLevel = manager.getTopLevelFile(position);
            host = topLevel == null || topLevel == position.getContainingFile() ? null : topLevel.getContext();
        }
        return host != null
                && PsiTreeUtil.getParentOfType(host, JSStringTemplateExpression.class, false) != null;
    }
}
