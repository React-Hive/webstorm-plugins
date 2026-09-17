package com.reacthive.honeystyle;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick documentation (Ctrl+Q) for honey at-rule completion items.
 *
 * <p>These items have no PSI to hang documentation on - they are plain strings produced by
 * {@link HoneyAtRuleCompletionContributor} - so a {@link FakePsiElement} carries the key across,
 * the same approach the platform's own live template documentation uses. Registered through the
 * language-independent {@code documentationProvider} extension point because the caret may be in an
 * injected CSS fragment rather than the host TypeScript file.
 */
public final class HoneyAtRuleDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(@NotNull PsiManager psiManager,
                                                                     Object object,
                                                                     @Nullable PsiElement element) {
        if (!(object instanceof String key) || element == null) {
            return null;
        }
        return documentation(key, element.getProject()) == null ? null : new LookupDoc(element, key);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return element instanceof LookupDoc doc ? documentation(doc.key, doc.getProject()) : null;
    }

    private static @Nullable String documentation(String key, Project project) {
        HoneyAtRules.AtRule rule = HoneyAtRules.byName(key);
        if (rule != null) {
            return atRuleDoc(rule);
        }
        if (HoneyAtRules.MEDIA_ORIENTATIONS.contains(key)) {
            return section("@honey-media orientation", "orientation: " + key);
        }
        if (HoneyAtRules.MEDIA_TYPES.contains(key)) {
            return section("@honey-media type", "media type <code>" + key + "</code>");
        }
        if (HoneyAtRules.CENTER_AXES.contains(key)) {
            return section("@honey-center axis", "centers on the " + key + " axis");
        }
        return breakpointDoc(key, project);
    }

    private static @Nullable String breakpointDoc(String key, Project project) {
        String[] parts = HoneyAtRules.splitBreakpointToken(key);
        String direction = parts[1];
        if (direction != null && !HoneyAtRules.BREAKPOINT_DIRECTIONS.contains(direction)) {
            return null;
        }
        for (HoneyBreakpoint breakpoint : HoneyThemeService.getInstance(project).getBreakpoints()) {
            if (!breakpoint.name().equals(parts[0])) {
                continue;
            }
            StringBuilder body = new StringBuilder();
            body.append("<code>@media (").append(HoneyAtRules.mediaFeature(breakpoint, direction)).append(")</code>");
            body.append("<br/><br/>Breakpoint <code>").append(breakpoint.name())
                    .append("</code> is <code>").append(breakpoint.cssValue())
                    .append("</code> in the project theme.");
            if (direction == null) {
                body.append("<br/>No direction given, so <code>:up</code> is used.");
            }
            return section("@honey-media breakpoint", body.toString());
        }
        return null;
    }

    private static String atRuleDoc(HoneyAtRules.AtRule rule) {
        StringBuilder body = new StringBuilder();
        body.append("Compiles to ").append(rule.expansion()).append('.');
        if (!rule.params().isEmpty()) {
            body.append("<br/><br/>Parameters: <code>").append(rule.params()).append("</code>");
        } else {
            body.append("<br/><br/>Takes no parameters.");
        }
        return section("@" + rule.name(), body.toString());
    }

    private static String section(String title, String body) {
        return "<div><b>" + title + "</b></div><br/><div>" + body + "</div>";
    }

    /**
     * Carries a completion item's key so {@link #generateDoc} can describe it.
     *
     * <p>{@code serial} is suppressed because the platform's PSI base classes are incidentally
     * {@link java.io.Serializable}; PSI is never actually serialized.
     */
    @SuppressWarnings("serial")
    private static final class LookupDoc extends FakePsiElement {

        private final PsiElement context;
        private final String key;

        private LookupDoc(PsiElement context, String key) {
            this.context = context;
            this.key = key;
        }

        @Override
        public PsiElement getParent() {
            return context;
        }
    }
}
