package com.reacthive.honeystyle;

import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * Puts a color swatch in the gutter next to a honey theme color path, and lets the gutter's color
 * picker rewrite the path.
 *
 * <p>Because only theme tokens are valid here, picking an arbitrary color snaps the path to the
 * nearest token in the palette rather than writing a raw hex value.
 */
public final class HoneyElementColorProvider implements ElementColorProvider {

    @Override
    public @Nullable Color getColorFrom(@NotNull PsiElement element) {
        HoneyColorPaths.Reference reference = referenceAt(element);
        if (reference == null) {
            return null;
        }
        HoneyColorEntry entry = HoneyThemeService.getInstance(element.getProject())
                .getPalette()
                .find(reference.path(), reference.root());
        if (entry == null) {
            return null;
        }
        Double alpha = reference.alpha();
        return alpha == null ? entry.color() : CssColorParser.withAlpha(entry.color(), alpha);
    }

    @Override
    public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
        HoneyColorPaths.Reference reference = referenceAt(element);
        if (reference == null) {
            return;
        }
        Project project = element.getProject();
        HoneyPalette palette = HoneyThemeService.getInstance(project).getPalette();
        HoneyColorEntry current = palette.find(reference.path(), reference.root());
        if (current == null) {
            return;
        }

        Color target = reference.alpha() == null ? color : new Color(color.getRGB(), false);
        HoneyColorEntry nearest = palette.nearest(target, reference.root());
        if (nearest == null || nearest.path().equals(current.path())) {
            return;
        }

        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null || !document.isWritable()) {
            return;
        }
        TextRange range = reference.replaceRange();
        String replacement = nearest.path();
        WriteCommandAction.runWriteCommandAction(
                project,
                "Change Honey Style Color",
                null,
                () -> {
                    document.replaceString(range.getStartOffset(), range.getEndOffset(), replacement);
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                },
                file);
    }

    /**
     * Cheap pre-filter first: this runs for every leaf in the file during the line marker pass.
     */
    private static @Nullable HoneyColorPaths.Reference referenceAt(@NotNull PsiElement element) {
        if (element.getFirstChild() != null) {
            return null;
        }
        PsiElement parent = element.getParent();
        if (parent instanceof JSReferenceExpression reference) {
            if (reference.getQualifier() == null) {
                return null;
            }
        } else if (!(parent instanceof JSLiteralExpression) || parent instanceof JSStringTemplateExpression) {
            // A template expression is a JSLiteralExpression; its text is the line marker
            // provider's job, so claiming it here would draw a second swatch.
            return null;
        }
        if (!HoneyStyleSettings.getInstance(element.getProject()).isEnabled()) {
            return null;
        }
        return HoneyColorPaths.fromLeaf(element);
    }
}
