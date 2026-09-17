package com.reacthive.honeystyle;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds a gutter swatch for theme paths written as plain text inside a styled/css template literal:
 *
 * <pre>
 *   const Tab = styled.div`
 *     border-color: secondary.mediumGreen;
 *   `;
 * </pre>
 *
 * <p>{@link com.intellij.openapi.editor.ElementColorProvider} cannot be used here - a template
 * chunk is one PSI element spanning many lines, so each occurrence needs its own marker range.
 * Chunks are read by walking the template's leaf children rather than {@code getStringRanges()},
 * whose offsets are not documented as relative or absolute; interpolations are composite children
 * and are skipped, so a path inside {@code ${...}} is left to the color provider.
 */
public final class HoneyTemplateColorLineMarkerProvider implements LineMarkerProvider {

    private static final Pattern DOTTED_PATH =
            Pattern.compile("\\b[A-Za-z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)+\\b");

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super LineMarkerInfo<?>> result) {
        HoneyPalette palette = null;
        Set<Integer> emitted = new HashSet<>();
        for (PsiElement element : elements) {
            if (!(element instanceof JSStringTemplateExpression template)) {
                continue;
            }
            if (palette == null) {
                Project project = element.getProject();
                HoneyStyleSettings settings = HoneyStyleSettings.getInstance(project);
                if (!settings.isEnabled() || !settings.isShowInTemplates()) {
                    return;
                }
                palette = HoneyThemeService.getInstance(project).getPalette();
                if (palette.isEmpty()) {
                    return;
                }
            }
            collectFromTemplate(template, palette, result, emitted);
        }
    }

    private static void collectFromTemplate(JSStringTemplateExpression template,
                                            HoneyPalette palette,
                                            Collection<? super LineMarkerInfo<?>> result,
                                            Set<Integer> emitted) {
        for (PsiElement child = template.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getFirstChild() != null) {
                continue;
            }
            String text = child.getText();
            if (text.isEmpty() || "`".equals(text) || "${".equals(text) || "}".equals(text)) {
                continue;
            }
            collectFromChunk(child, text, palette, result, emitted);
        }
    }

    private static void collectFromChunk(PsiElement chunk,
                                         String text,
                                         HoneyPalette palette,
                                         Collection<? super LineMarkerInfo<?>> result,
                                         Set<Integer> emitted) {
        int chunkStart = chunk.getTextRange().getStartOffset();
        Matcher matcher = DOTTED_PATH.matcher(text);
        while (matcher.find()) {
            HoneyPathMatcher.PathMatch match = HoneyPathMatcher.match(palette, matcher.group());
            if (match == null) {
                continue;
            }
            int start = chunkStart + matcher.start() + match.offset();
            // Nested templates can present the same text twice; one swatch per position.
            if (emitted.add(start)) {
                result.add(createMarker(chunk, new TextRange(start, start + match.length()), match.entry()));
            }
        }
    }

    private static LineMarkerInfo<PsiElement> createMarker(PsiElement anchor,
                                                           TextRange range,
                                                           HoneyColorEntry entry) {
        String tooltip = entry.path() + " \u2192 " + CssColorParser.toHex(entry.color())
                + " (" + entry.sourceName() + ")";
        return new ColorMarker(anchor, range, JBUIScale.scaleIcon(new ColorIcon(12, entry.color())),
                tooltip, entry.color(), entry.path());
    }

    /**
     * The line marker pass can run more than once over the same element - the platform's own color
     * markers are mergeable for exactly this reason. Without merging, one path draws two swatches.
     * Only an identical range and color merge, so two different paths on one line stay separate.
     */
    private static final class ColorMarker extends MergeableLineMarkerInfo<PsiElement> {

        private final TextRange range;
        private final Color color;
        private final Icon swatch;
        private final String tooltip;

        private ColorMarker(PsiElement anchor,
                            TextRange range,
                            Icon swatch,
                            String tooltip,
                            Color color,
                            String path) {
            super(anchor, range, swatch, element -> tooltip, null,
                    GutterIconRenderer.Alignment.LEFT, () -> "Honey theme color " + path);
            this.range = range;
            this.color = color;
            this.swatch = swatch;
            this.tooltip = tooltip;
        }

        @Override
        public boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info) {
            return info instanceof ColorMarker other
                    && range.equals(other.range)
                    && color.equals(other.color);
        }

        @Override
        public Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
            return swatch;
        }

        @Override
        public @NotNull Function<? super PsiElement, String> getCommonTooltip(
                @NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
            return element -> tooltip;
        }
    }
}
