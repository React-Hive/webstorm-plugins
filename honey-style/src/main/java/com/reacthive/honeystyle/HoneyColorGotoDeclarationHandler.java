package com.reacthive.honeystyle;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cmd/Ctrl+Click on a theme color path jumps to the declaration that holds the value, e.g.
 * {@code royalBlue: '#318BFA'} in the theme file.
 *
 * <p>This also covers positions TypeScript cannot reach: a path written as plain text inside a
 * styled/css template literal. Where TypeScript does resolve the path it lands on the key type
 * rather than the value, so the value is offered instead.
 */
public final class HoneyColorGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                  int offset,
                                                  Editor editor) {
        if (sourceElement == null || sourceElement.getFirstChild() != null) {
            return null;
        }
        Project project = sourceElement.getProject();
        if (!HoneyStyleSettings.getInstance(project).isEnabled()) {
            return null;
        }

        HoneyColorEntry entry = resolveEntry(sourceElement, offset, project);
        if (entry == null) {
            return null;
        }
        PsiElement target = declarationOf(project, entry);
        return target == null ? null : new PsiElement[]{target};
    }

    @Override
    public @Nullable String getActionText(@NotNull DataContext context) {
        return null;
    }

    private static @Nullable HoneyColorEntry resolveEntry(PsiElement leaf, int offset, Project project) {
        HoneyColorPaths.Reference reference = HoneyColorPaths.fromLeaf(leaf);
        HoneyPalette palette = HoneyThemeService.getInstance(project).getPalette();
        if (palette.isEmpty()) {
            return null;
        }
        if (reference != null) {
            HoneyColorEntry entry = palette.find(reference.path(), reference.root());
            if (entry != null) {
                return entry;
            }
        }
        return insideStringContent(leaf) ? tokenAt(leaf, offset, palette) : null;
    }

    private static boolean insideStringContent(PsiElement leaf) {
        PsiElement parent = leaf.getParent();
        return parent instanceof JSStringTemplateExpression || parent instanceof JSLiteralExpression;
    }

    /**
     * Picks the dotted token under the caret out of raw string text.
     */
    private static @Nullable HoneyColorEntry tokenAt(PsiElement leaf, int offset, HoneyPalette palette) {
        String text = leaf.getText();
        int local = offset - leaf.getTextRange().getStartOffset();
        if (local < 0 || local > text.length()) {
            return null;
        }
        int start = local;
        while (start > 0 && isPathChar(text.charAt(start - 1))) {
            start--;
        }
        int end = local;
        while (end < text.length() && isPathChar(text.charAt(end))) {
            end++;
        }
        if (start >= end) {
            return null;
        }
        String token = text.substring(start, end);
        HoneyPathMatcher.PathMatch match = HoneyPathMatcher.match(palette, token);
        return match != null && match.contains(local - start) ? match.entry() : null;
    }

    private static boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.';
    }

    private static @Nullable PsiElement declarationOf(Project project, HoneyColorEntry entry) {
        if (entry.sourceFile() == null) {
            return null;
        }
        PsiFile file = PsiManager.getInstance(project).findFile(entry.sourceFile());
        if (file == null) {
            return null;
        }
        return HoneyThemeIndexer.findDeclaration(file, entry.rootName(), entry.path());
    }
}
