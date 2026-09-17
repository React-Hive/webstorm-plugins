package com.reacthive.honeystyle;

import com.intellij.lang.javascript.psi.JSArgumentList;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Recognises the ways a honey theme color path is written in JS/TS source.
 *
 * <pre>
 *   colors.primary.royalBlue                 // destructured `theme: { colors }`
 *   theme.colors.neutral.fogGrey
 *   theme.mg.colors2.primary.main
 *   resolveColor('primary.royalBlue', 0.25)  // alpha is applied to the swatch
 *   getColor('primary.main')
 *   $color="secondary.mediumGreen"           // any string literal that is a known path
 * </pre>
 */
public final class HoneyColorPaths {

    /** Object names that introduce a palette; the segments after them form the path. */
    private static final Set<String> PALETTE_ROOTS = Set.of("colors", "colors2", "colours", "palette");

    /** Names that may precede a path, including wrappers that are not palettes themselves. */
    private static final Set<String> ANCHORS = Set.of("colors", "colors2", "colours", "palette", "theme");

    private static final Set<String> COLOR_FUNCTIONS = Set.of("resolveColor", "getColor", "getContrastColor");

    private HoneyColorPaths() {
    }

    /**
     * @param path         dotted path to look up, e.g. {@code primary.royalBlue}
     * @param root         the palette named in the source, or {@code null} when not stated
     * @param alpha        alpha passed to {@code resolveColor(path, alpha)}, or {@code null}
     * @param replaceRange the range holding just the path text, for rewriting on color change
     */
    public record Reference(@NotNull String path,
                            @Nullable String root,
                            @Nullable Double alpha,
                            @NotNull TextRange replaceRange) {
    }

    /**
     * Resolves a reference from a leaf element. Only leaves are handled so a single dotted
     * expression contributes exactly one swatch.
     */
    public static @Nullable Reference fromLeaf(@NotNull PsiElement leaf) {
        PsiElement parent = leaf.getParent();
        if (parent instanceof JSReferenceExpression reference) {
            return fromReference(leaf, reference);
        }
        if (parent instanceof JSLiteralExpression literal) {
            return fromLiteral(leaf, literal);
        }
        return null;
    }

    private static @Nullable Reference fromReference(PsiElement leaf, JSReferenceExpression reference) {
        if (reference.getQualifier() == null || reference.getReferenceNameElement() != leaf) {
            return null;
        }

        List<JSReferenceExpression> chain = new ArrayList<>();
        JSExpression current = reference;
        while (current instanceof JSReferenceExpression link) {
            chain.add(link);
            current = link.getQualifier();
        }
        Collections.reverse(chain);

        List<String> segments = new ArrayList<>(chain.size());
        for (JSReferenceExpression link : chain) {
            String name = link.getReferenceName();
            if (name == null) {
                return null;
            }
            segments.add(name);
        }
        if (segments.size() < 2) {
            return null;
        }

        int anchor = -1;
        for (int i = segments.size() - 2; i >= 0; i--) {
            if (ANCHORS.contains(segments.get(i))) {
                anchor = i;
                break;
            }
        }
        if (anchor < 0 && segments.size() != 2) {
            return null;
        }

        int pathStart = anchor + 1;
        String root = anchor >= 0 && PALETTE_ROOTS.contains(segments.get(anchor)) ? segments.get(anchor) : null;
        String path = String.join(".", segments.subList(pathStart, segments.size()));

        PsiElement firstName = chain.get(pathStart).getReferenceNameElement();
        if (firstName == null) {
            return null;
        }
        TextRange range = new TextRange(firstName.getTextRange().getStartOffset(),
                reference.getTextRange().getEndOffset());
        return new Reference(path, root, null, range);
    }

    private static @Nullable Reference fromLiteral(PsiElement leaf, JSLiteralExpression literal) {
        if (!literal.isStringLiteral() || literal.getFirstChild() != leaf) {
            return null;
        }
        String value = literal.getStringValue();
        if (value == null || value.indexOf('.') < 0) {
            return null;
        }

        String root = null;
        Double alpha = null;
        if (literal.getParent() instanceof JSArgumentList arguments
                && arguments.getParent() instanceof JSCallExpression call) {
            String function = calleeName(call);
            if (function != null && COLOR_FUNCTIONS.contains(function)) {
                if ("resolveColor".equals(function)) {
                    root = "colors";
                }
                JSExpression[] args = arguments.getArguments();
                if (args.length > 1 && args[0] == literal) {
                    alpha = HoneyThemeIndexer.numericValue(args[1]);
                }
            }
        }
        return new Reference(value, root, alpha, unquotedRange(literal));
    }

    /**
     * The palette prefix a completion should be offered under.
     *
     * @param path the group path already typed, e.g. {@code primary} in {@code colors.primary.},
     *             or empty when whole paths are expected
     */
    public record Prefix(@NotNull String path, @Nullable String root) {
    }

    /**
     * Reads the prefix from the qualifier left of the caret, so {@code colors.primary.} offers the
     * names inside {@code primary}.
     */
    public static @Nullable Prefix qualifierPrefix(@NotNull JSReferenceExpression qualifier) {
        List<String> segments = new ArrayList<>();
        JSExpression current = qualifier;
        while (current instanceof JSReferenceExpression link) {
            String name = link.getReferenceName();
            if (name == null) {
                return null;
            }
            segments.add(0, name);
            current = link.getQualifier();
        }
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (ANCHORS.contains(segments.get(i))) {
                String root = PALETTE_ROOTS.contains(segments.get(i)) ? segments.get(i) : null;
                return new Prefix(String.join(".", segments.subList(i + 1, segments.size())), root);
            }
        }
        return null;
    }

    /** True when the caret sits in a string argument of a honey color function. */
    public static boolean isColorFunctionArgument(@NotNull JSLiteralExpression literal) {
        if (!(literal.getParent() instanceof JSArgumentList arguments)
                || !(arguments.getParent() instanceof JSCallExpression call)) {
            return false;
        }
        String function = calleeName(call);
        return function != null && COLOR_FUNCTIONS.contains(function);
    }

    private static @Nullable String calleeName(JSCallExpression call) {
        JSExpression callee = call.getMethodExpression();
        return callee instanceof JSReferenceExpression reference ? reference.getReferenceName() : null;
    }

    private static TextRange unquotedRange(JSLiteralExpression literal) {
        TextRange full = literal.getTextRange();
        String text = literal.getText();
        if (text.length() >= 2 && isQuote(text.charAt(0)) && text.charAt(0) == text.charAt(text.length() - 1)) {
            return new TextRange(full.getStartOffset() + 1, full.getEndOffset() - 1);
        }
        return full;
    }

    private static boolean isQuote(char c) {
        return c == '\'' || c == '"' || c == '`';
    }
}
