package com.reacthive.honeystyle;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Everything the plugin reads out of the project's theme sources.
 *
 * @param palette     color paths
 * @param breakpoints breakpoint keys in declaration order, used by {@code @honey-media}
 */
public record HoneyThemeModel(@NotNull HoneyPalette palette, @NotNull List<HoneyBreakpoint> breakpoints) {

    public static final HoneyThemeModel EMPTY = new HoneyThemeModel(HoneyPalette.EMPTY, List.of());

    public boolean isEmpty() {
        return palette.isEmpty() && breakpoints.isEmpty();
    }
}
