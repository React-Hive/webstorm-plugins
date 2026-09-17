package com.reacthive.honeystyle;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
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

import java.awt.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
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

    private static final int THEME_GROUP = 2;
    private static final int CSS_COLOR_GROUP = 1;
    private static final double THEME_PRIORITY = 100;
    private static final double CSS_COLOR_PRIORITY = 50;

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
        Map<String, String> customColors = HoneyStyleSettings.getInstance(project).getCustomColorMap();

        // Every item is re-emitted through this one sorter. Passing a result straight through would
        // keep the sorter of whichever contributor produced it - CompletionResult carries its own -
        // and TypeScript's does not weigh priority, so the tiers would be ignored.
        CompletionSorter sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher());

        result.runRemainingContributors(parameters, completionResult -> {
            LookupElement element = completionResult.getLookupElement();
            String lookup = element.getLookupString();

            HoneyColorEntry entry = palette.find(fullPath(prefix, lookup), prefix.root());
            Color color = entry != null ? entry.color() : null;
            if (color == null && prefix.allowsCssColor()) {
                // TypeScript offers the CSS color names here from `HoneyCssColor`.
                color = CssColorParser.parse(lookup, customColors);
            }
            if (entry != null) {
                offered.add(entry.path());
            }

            LookupElement emitted = element;
            if (color != null) {
                boolean theme = entry != null;
                emitted = prioritized(withSwatch(element, color),
                        theme ? THEME_GROUP : CSS_COLOR_GROUP,
                        theme ? THEME_PRIORITY : CSS_COLOR_PRIORITY);
            }
            // The result's own prefix matcher is kept, so nothing is filtered out by re-adding it.
            result.withPrefixMatcher(completionResult.getPrefixMatcher())
                    .withRelevanceSorter(sorter)
                    .addElement(emitted);
        });

        CompletionResultSet ours = result.withRelevanceSorter(sorter);
        for (HoneyColorEntry entry : palette.entries()) {
            if (!belongsUnder(entry.path(), prefix.path()) || offered.contains(entry.path())) {
                continue;
            }
            ours.addElement(prioritized(create(entry, prefix), THEME_GROUP, THEME_PRIORITY));
        }
    }

    private static @Nullable HoneyColorPaths.Prefix prefixAt(PsiElement position) {
        PsiElement parent = position.getParent();
        if (parent instanceof JSLiteralExpression literal) {
            return HoneyColorPaths.isColorFunctionArgument(literal)
                    ? new HoneyColorPaths.Prefix("", "colors", true)
                    : null;
        }
        if (parent instanceof XmlAttributeValue attributeValue) {
            return HoneyColorPaths.isColorProp(attributeValue)
                    ? new HoneyColorPaths.Prefix("", null, true)
                    : null;
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

    /**
     * A path belongs under a prefix when it is exactly one segment deeper.
     */
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

    /**
     * Theme tokens sort above CSS color names, which in turn sort above everything else TypeScript
     * offers here - {@code inherit}, {@code unset}, and the 38 deprecated system colors such as
     * {@code Background} that csstype's {@code Property.Color} pulls in.
     *
     * <p>Grouping rather than priority alone: priority is only one weigher among several, so on an
     * empty prefix the platform's other weighers can still float an ungrouped item to the top.
     * Ordering within a tier is left to the platform, so prefix and camel-hump matching keep working.
     */
    private static LookupElement prioritized(LookupElement element, int group, double priority) {
        return PrioritizedLookupElement.withGrouping(
                PrioritizedLookupElement.withPriority(element, priority), group);
    }

    private static LookupElement withSwatch(LookupElement element, Color color) {
        return LookupElementDecorator.withRenderer(element,
                new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
                    @Override
                    public void renderElement(LookupElementDecorator<LookupElement> decorator,
                                              LookupElementPresentation presentation) {
                        decorator.getDelegate().renderElement(presentation);
                        presentation.setIcon(new ColorIcon(12, color));
                        presentation.setTypeText(CssColorParser.toHex(color));
                    }
                });
    }
}
