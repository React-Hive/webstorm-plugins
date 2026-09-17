package com.reacthive.honeystyle;

import com.intellij.lang.javascript.psi.JSArgumentList;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds a {@link HoneyPalette} by reading the project's own theme sources.
 *
 * <p>Nothing is hardcoded: any object literal called {@code colors}, {@code colors2} or
 * {@code palette} - either a variable or a property of a theme object - is flattened into
 * dotted paths, so the plugin follows the project as its palette evolves.
 */
final class HoneyThemeIndexer {

    private static final Set<String> PALETTE_ROOTS = Set.of("colors", "colors2", "colours", "palette");
    private static final String BREAKPOINTS_ROOT = "breakpoints";
    private static final List<String> EXTENSIONS = List.of("ts", "tsx", "js", "mjs");
    private static final int MAX_FILES = 60;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_REFERENCE_HOPS = 4;

    private HoneyThemeIndexer() {
    }

    /**
     * @param palette      the flattened palette
     * @param scannedFiles every file that was parsed, so the cache can depend on exactly those
     */
    record IndexResult(@NotNull HoneyThemeModel model, @NotNull List<PsiFile> scannedFiles) {
    }

    static @NotNull IndexResult build(@NotNull Project project, @NotNull HoneyStyleSettings settings) {
        List<VirtualFile> files = discover(project, settings);
        if (files.isEmpty()) {
            return new IndexResult(HoneyThemeModel.EMPTY, List.of());
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        List<HoneyColorEntry> entries = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<PsiFile> scanned = new ArrayList<>();
        Map<String, HoneyBreakpoint> breakpoints = new LinkedHashMap<>();

        for (VirtualFile file : files) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                continue;
            }
            scanned.add(psiFile);
            int before = entries.size();
            collect(psiFile, presentableName(project, file), file, entries, breakpoints);
            if (entries.size() > before) {
                sources.add(presentableName(project, file));
            }
        }
        HoneyPalette palette = entries.isEmpty() ? HoneyPalette.EMPTY : new HoneyPalette(entries, sources);
        return new IndexResult(new HoneyThemeModel(palette, List.copyOf(breakpoints.values())), scanned);
    }

    // --- discovery -------------------------------------------------------------------------

    private static List<VirtualFile> discover(Project project, HoneyStyleSettings settings) {
        Set<VirtualFile> pinned = new LinkedHashSet<>();
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            for (String relative : settings.getExtraThemeFiles()) {
                String trimmed = relative == null ? "" : relative.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                VirtualFile file = baseDir.findFileByRelativePath(trimmed);
                if (file != null && !file.isDirectory()) {
                    pinned.add(file);
                }
            }
        }

        String filter = settings.getDiscoveryFilter().toLowerCase(Locale.ROOT);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        List<ScoredFile> scored = new ArrayList<>();

        for (String extension : EXTENSIONS) {
            Collection<VirtualFile> found;
            try {
                found = FilenameIndex.getAllFilesByExt(project, extension, scope);
            } catch (IndexNotReadyException ignored) {
                return new ArrayList<>(pinned);
            }
            for (VirtualFile file : found) {
                if (pinned.contains(file)) {
                    continue;
                }
                String path = file.getPath().toLowerCase(Locale.ROOT);
                if (path.contains("/node_modules/") || !path.contains(filter)) {
                    continue;
                }
                scored.add(new ScoredFile(file, score(path)));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<VirtualFile> result = new ArrayList<>(pinned);
        for (ScoredFile candidate : scored) {
            if (result.size() >= MAX_FILES) {
                break;
            }
            result.add(candidate.file);
        }
        return result;
    }

    /**
     * Ranks candidates so the real palette wins over mocks, docs and build output when several
     * files declare the same path. Pinned files always sort first because they skip this entirely.
     */
    private static int score(String lowerCasePath) {
        int score = 0;
        if (lowerCasePath.contains("mock") || lowerCasePath.contains("fixture")) {
            score -= 200;
        }
        if (lowerCasePath.contains("/docs/") || lowerCasePath.contains("/doc/")
                || lowerCasePath.contains("example") || lowerCasePath.contains("storybook")
                || lowerCasePath.contains(".stories.")) {
            score -= 150;
        }
        if (lowerCasePath.contains(".test.") || lowerCasePath.contains(".spec.")
                || lowerCasePath.contains("/__tests__/")) {
            score -= 150;
        }
        if (lowerCasePath.contains("/dist/") || lowerCasePath.contains("/build/")
                || lowerCasePath.contains("/out/") || lowerCasePath.contains("/lib/")) {
            score -= 120;
        }
        if (lowerCasePath.contains("/packages/")) {
            score -= 60;
        }
        if (lowerCasePath.contains("/libs/") || lowerCasePath.contains("/apps/")) {
            score += 40;
        }
        if (lowerCasePath.contains("/src/")) {
            score += 20;
        }
        if (lowerCasePath.contains("honey")) {
            score += 15;
        }
        return score;
    }

    private static String presentableName(Project project, VirtualFile file) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            String base = baseDir.getPath();
            String path = file.getPath();
            if (path.startsWith(base + "/")) {
                return path.substring(base.length() + 1);
            }
        }
        return file.getName();
    }

    // --- parsing ---------------------------------------------------------------------------

    private static void collect(PsiFile file,
                                String sourceName,
                                VirtualFile sourceFile,
                                List<HoneyColorEntry> out,
                                Map<String, HoneyBreakpoint> breakpointsOut) {
        for (JSObjectLiteralExpression object : PsiTreeUtil.findChildrenOfType(file, JSObjectLiteralExpression.class)) {
            String rootName = declaredName(object);
            if (rootName == null) {
                continue;
            }
            if (PALETTE_ROOTS.contains(rootName)) {
                flatten(object, "", rootName, sourceName, sourceFile, out, 0);
            } else if (BREAKPOINTS_ROOT.equals(rootName)) {
                for (JSProperty property : object.getProperties()) {
                    String name = property.getName();
                    JSExpression value = property.getValue();
                    if (name == null || name.isEmpty() || value == null) {
                        continue;
                    }
                    breakpointsOut.putIfAbsent(name, new HoneyBreakpoint(name, unquote(value.getText().trim())));
                }
            }
        }
    }

    /**
     * @return the palette name if {@code object} is the value of a {@code colors}-like variable or
     *         property, otherwise {@code null}
     */
    private static @Nullable String declaredName(JSObjectLiteralExpression object) {
        PsiElement parent = object.getParent();
        if (parent instanceof JSProperty property) {
            return property.getName();
        }
        if (parent instanceof JSVariable variable) {
            return variable.getName();
        }
        return null;
    }

    private static @Nullable String paletteRootName(JSObjectLiteralExpression object) {
        String name = declaredName(object);
        return name != null && PALETTE_ROOTS.contains(name) ? name : null;
    }

    private static void flatten(JSObjectLiteralExpression object,
                                String prefix,
                                String rootName,
                                String sourceName,
                                VirtualFile sourceFile,
                                List<HoneyColorEntry> out,
                                int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        for (JSProperty property : object.getProperties()) {
            String name = property.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            JSExpression value = property.getValue();
            if (value instanceof JSObjectLiteralExpression nested) {
                flatten(nested, path, rootName, sourceName, sourceFile, out, depth + 1);
                continue;
            }
            String raw = resolveStringValue(value, 0);
            if (raw == null) {
                continue;
            }
            Color color = CssColorParser.parse(raw);
            if (color != null) {
                out.add(new HoneyColorEntry(path, rootName, color, raw, sourceName, sourceFile));
            }
        }
    }

    /**
     * Resolves a property value to a color string, following {@code const white = '#FFFFFF'}
     * aliases and {@code hexWithAlpha('#e53935', 0.1)} calls.
     */
    private static @Nullable String resolveStringValue(@Nullable JSExpression expression, int hops) {
        if (expression == null || hops > MAX_REFERENCE_HOPS) {
            return null;
        }
        if (expression instanceof JSLiteralExpression literal && literal.isStringLiteral()) {
            return literal.getStringValue();
        }
        if (expression instanceof JSReferenceExpression reference) {
            PsiElement resolved = reference.resolve();
            if (resolved instanceof JSVariable variable) {
                return resolveStringValue(variable.getInitializer(), hops + 1);
            }
            return null;
        }
        if (expression instanceof JSCallExpression call) {
            return resolveCall(call, hops);
        }
        return null;
    }

    private static @Nullable String resolveCall(JSCallExpression call, int hops) {
        JSExpression callee = call.getMethodExpression();
        String function = callee instanceof JSReferenceExpression reference
                ? reference.getReferenceName()
                : null;
        if (!"hexWithAlpha".equals(function)) {
            return null;
        }
        JSArgumentList argumentList = call.getArgumentList();
        if (argumentList == null) {
            return null;
        }
        JSExpression[] arguments = argumentList.getArguments();
        if (arguments.length < 2) {
            return null;
        }
        String base = resolveStringValue(arguments[0], hops + 1);
        Double alpha = numericValue(arguments[1]);
        if (base == null || alpha == null) {
            return null;
        }
        Color color = CssColorParser.parse(base);
        return color == null ? null : CssColorParser.toHex(CssColorParser.withAlpha(color, alpha));
    }

    private static String unquote(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            if ((first == '\'' || first == '"' || first == '`') && text.charAt(text.length() - 1) == first) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    static @Nullable Double numericValue(@Nullable JSExpression expression) {
        if (expression instanceof JSLiteralExpression literal && literal.isNumericLiteral()) {
            Object value = literal.getValue();
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(literal.getText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Re-walks a theme file to locate the property that declares {@code path}, for navigation.
     */
    static @Nullable JSProperty findDeclaration(@NotNull PsiFile file,
                                                @NotNull String rootName,
                                                @NotNull String path) {
        String[] segments = path.split("\\.");
        if (segments.length == 0) {
            return null;
        }
        for (JSObjectLiteralExpression object : PsiTreeUtil.findChildrenOfType(file, JSObjectLiteralExpression.class)) {
            if (!rootName.equals(paletteRootName(object))) {
                continue;
            }
            JSProperty found = descend(object, segments, 0);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable JSProperty descend(JSObjectLiteralExpression object, String[] segments, int index) {
        JSProperty property = propertyNamed(object, segments[index]);
        if (property == null) {
            return null;
        }
        if (index == segments.length - 1) {
            return property;
        }
        return property.getValue() instanceof JSObjectLiteralExpression nested
                ? descend(nested, segments, index + 1)
                : null;
    }

    private static @Nullable JSProperty propertyNamed(JSObjectLiteralExpression object, String name) {
        for (JSProperty property : object.getProperties()) {
            if (name.equals(property.getName())) {
                return property;
            }
        }
        return null;
    }

    private record ScoredFile(VirtualFile file, int score) {
    }
}
