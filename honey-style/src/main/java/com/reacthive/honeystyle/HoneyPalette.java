package com.reacthive.honeystyle;

import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of every color path found in the project's honey theme files.
 *
 * <p>A project can declare more than one palette, and separate palettes may reuse path names like
 * {@code secondary.light} for different values. Entries are therefore indexed both by bare path and
 * by {@code <root>.<path>}, so a reference resolves against the palette it actually names.
 */
public final class HoneyPalette {

    public static final HoneyPalette EMPTY = new HoneyPalette(List.of(), List.of());

    private final Map<String, HoneyColorEntry> byPath;
    private final Map<String, HoneyColorEntry> byQualifiedPath;
    private final List<String> sourceFiles;

    /** @param entries in priority order - the first entry for a given path wins. */
    public HoneyPalette(List<HoneyColorEntry> entries, List<String> sourceFiles) {
        Map<String, HoneyColorEntry> paths = new LinkedHashMap<>();
        Map<String, HoneyColorEntry> qualified = new LinkedHashMap<>();
        for (HoneyColorEntry entry : entries) {
            paths.putIfAbsent(entry.path(), entry);
            qualified.putIfAbsent(entry.qualifiedPath(), entry);
        }
        this.byPath = Map.copyOf(paths);
        this.byQualifiedPath = Map.copyOf(qualified);
        this.sourceFiles = List.copyOf(sourceFiles);
    }

    public boolean isEmpty() {
        return byPath.isEmpty();
    }

    public int size() {
        return byPath.size();
    }

    public List<String> sourceFiles() {
        return sourceFiles;
    }

    public Collection<HoneyColorEntry> entries() {
        return byPath.values();
    }

    public @Nullable HoneyColorEntry find(@Nullable String path) {
        return find(path, null);
    }

    /**
     * @param root the palette named in the reference ({@code colors} in
     *             {@code theme.colors.primary.royalBlue}), or {@code null} when unknown
     */
    public @Nullable HoneyColorEntry find(@Nullable String path, @Nullable String root) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (root != null) {
            HoneyColorEntry exact = byQualifiedPath.get(root + "." + path);
            if (exact != null) {
                return exact;
            }
        }
        return byPath.get(path);
    }

    /**
     * Finds the theme path whose color is perceptually closest to {@code target}.
     * Used to snap a hand-picked color back onto a design token.
     */
    public @Nullable HoneyColorEntry nearest(Color target, @Nullable String root) {
        HoneyColorEntry best = null;
        double bestDistance = Double.MAX_VALUE;
        Collection<HoneyColorEntry> pool = byPath.values();
        for (HoneyColorEntry entry : pool) {
            if (root != null && !root.equals(entry.rootName())) {
                continue;
            }
            double distance = distance(entry.color(), target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry;
            }
        }
        return best != null ? best : nearestAnywhere(target);
    }

    private @Nullable HoneyColorEntry nearestAnywhere(Color target) {
        HoneyColorEntry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (HoneyColorEntry entry : byPath.values()) {
            double distance = distance(entry.color(), target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry;
            }
        }
        return best;
    }

    /** "Redmean" approximation - cheap, and much closer to human perception than plain RGB distance. */
    private static double distance(Color a, Color b) {
        double rMean = (a.getRed() + b.getRed()) / 2.0;
        double dr = a.getRed() - b.getRed();
        double dg = a.getGreen() - b.getGreen();
        double db = a.getBlue() - b.getBlue();
        double da = a.getAlpha() - b.getAlpha();
        return (2 + rMean / 256) * dr * dr
                + 4 * dg * dg
                + (2 + (255 - rMean) / 256) * db * db
                + da * da;
    }
}
