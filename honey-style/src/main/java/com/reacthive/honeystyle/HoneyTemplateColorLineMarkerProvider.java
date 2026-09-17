package com.reacthive.honeystyle;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
            collectFromTemplate(template, palette, result);
        }
    }

    private static void collectFromTemplate(JSStringTemplateExpression template,
                                            HoneyPalette palette,
                                            Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement child = template.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getFirstChild() != null) {
                continue;
            }
            String text = child.getText();
            if (text.isEmpty() || "`".equals(text) || "${".equals(text) || "}".equals(text)) {
                continue;
            }
            collectFromChunk(child, text, palette, result);
        }
    }

    private static void collectFromChunk(PsiElement chunk,
                                         String text,
                                         HoneyPalette palette,
                                         Collection<? super LineMarkerInfo<?>> result) {
        int chunkStart = chunk.getTextRange().getStartOffset();
        Matcher matcher = DOTTED_PATH.matcher(text);
        while (matcher.find()) {
            HoneyPathMatcher.PathMatch match = HoneyPathMatcher.match(palette, matcher.group());
            if (match == null) {
                continue;
            }
            int start = chunkStart + matcher.start() + match.offset();
            result.add(createMarker(chunk, new TextRange(start, start + match.length()), match.entry()));
        }
    }

    private static LineMarkerInfo<PsiElement> createMarker(PsiElement anchor,
                                                           TextRange range,
                                                           HoneyColorEntry entry) {
        String tooltip = entry.path() + " → " + CssColorParser.toHex(entry.color())
                + " (" + entry.sourceName() + ")";
        return new LineMarkerInfo<>(
                anchor,
                range,
                JBUIScale.scaleIcon(new ColorIcon(12, entry.color())),
                element -> tooltip,
                null,
                GutterIconRenderer.Alignment.LEFT,
                () -> "Honey theme color " + entry.path());
    }

}
