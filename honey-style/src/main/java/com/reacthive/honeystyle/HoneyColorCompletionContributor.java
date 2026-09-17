package com.reacthive.honeystyle;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Completes honey theme color paths, with the actual color shown as the item icon.
 *
 * <p>Two positions are handled:
 * <ul>
 *   <li>a string argument of {@code resolveColor} / {@code getColor} / {@code getContrastColor},
 *       where whole paths like {@code primary.royalBlue} are offered;</li>
 *   <li>member access after a palette group, e.g. {@code colors.primary.}, where the names inside
 *       that group are offered;</li>
 *   <li>a JSX color prop, e.g. {@code $backgroundColor="accent.mediumGold"}.</li>
 * </ul>
 *
 * <p>A project that augments {@code HoneyColors} with concrete key unions already gets these names
 * from the TypeScript service. This contributor runs first and decorates those items with a swatch
 * and the hex value instead of duplicating them; only names TypeScript did not offer are added.
 *
 * <p>Bare paths inside a css template literal are deliberately not completed - honey-style does not
 * resolve them at runtime, so completing them would suggest code that emits invalid CSS.
 */
public final class HoneyColorCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        Project project = position.getProject();
        if (!HoneyStyleSettings.getInstance(project).isEnabled()) {
            return;
        }
        HoneyColorPaths.Prefix prefix = prefixAt(position);
        if (prefix == null) {
            return;
        }
        HoneyPalette palette = HoneyThemeService.getInstance(project).getPalette();
        if (palette.isEmpty()) {
            return;
        }

        Set<String> offered = new HashSet<>();
        result.runRemainingContributors(parameters, completionResult -> {
            LookupElement element = completionResult.getLookupElement();
            HoneyColorEntry entry = palette.find(fullPath(prefix, element.getLookupString()), prefix.root());
            if (entry == null) {
                result.passResult(completionResult);
                return;
            }
            offered.add(entry.path());
            result.passResult(completionResult.withLookupElement(withSwatch(element, entry)));
        });

        for (HoneyColorEntry entry : palette.entries()) {
            if (!belongsUnder(entry.path(), prefix.path()) || offered.contains(entry.path())) {
                continue;
            }
            result.addElement(create(entry, prefix));
        }
    }

    private static @Nullable HoneyColorPaths.Prefix prefixAt(PsiElement position) {
        PsiElement parent = position.getParent();
        if (parent instanceof JSLiteralExpression literal) {
            return HoneyColorPaths.isColorFunctionArgument(literal)
                    ? new HoneyColorPaths.Prefix("", "colors")
                    : null;
        }
        if (parent instanceof XmlAttributeValue attributeValue) {
            return HoneyColorPaths.isColorProp(attributeValue) ? new HoneyColorPaths.Prefix("", null) : null;
        }
        if (parent instanceof JSReferenceExpression reference) {
            JSExpression qualifier = reference.getQualifier();
            if (qualifier instanceof JSReferenceExpression qualifierReference) {
                HoneyColorPaths.Prefix prefix = HoneyColorPaths.qualifierPrefix(qualifierReference);
                return prefix != null && !prefix.path().isEmpty() ? prefix : null;
            }
        }
        return null;
    }

    private static String fullPath(HoneyColorPaths.Prefix prefix, String lookupString) {
        return prefix.path().isEmpty() ? lookupString : prefix.path() + "." + lookupString;
    }

    /** A path belongs under a prefix when it is exactly one segment deeper. */
    private static boolean belongsUnder(String path, String prefix) {
        if (prefix.isEmpty()) {
            return true;
        }
        if (!path.startsWith(prefix + ".")) {
            return false;
        }
        return path.indexOf('.', prefix.length() + 1) < 0;
    }

    private static LookupElement create(HoneyColorEntry entry, HoneyColorPaths.Prefix prefix) {
        String insert = prefix.path().isEmpty()
                ? entry.path()
                : entry.path().substring(prefix.path().length() + 1);
        return LookupElementBuilder.create(insert)
                .withIcon(new ColorIcon(12, entry.color()))
                .withTypeText(CssColorParser.toHex(entry.color()));
    }

    private static LookupElement withSwatch(LookupElement element, HoneyColorEntry entry) {
        return LookupElementDecorator.withRenderer(element,
                new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
                    @Override
                    public void renderElement(LookupElementDecorator<LookupElement> decorator,
                                              LookupElementPresentation presentation) {
                        decorator.getDelegate().renderElement(presentation);
                        presentation.setIcon(new ColorIcon(12, entry.color()));
                        presentation.setTypeText(CssColorParser.toHex(entry.color()));
                    }
                });
    }
}
