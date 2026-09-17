package com.reacthive.honeystyle;

import org.jetbrains.annotations.NotNull;

/**
 * A breakpoint declared in {@code theme.breakpoints}, e.g. {@code sm: 768}.
 *
 * @param name     the key used in {@code @honey-media}
 * @param rawValue the value exactly as written in the theme
 */
public record HoneyBreakpoint(@NotNull String name, @NotNull String rawValue) {

    /** honey-style writes {@code `${breakpointPx}px`}, so a bare number gains a unit. */
    public @NotNull String cssValue() {
        return rawValue.chars().allMatch(c -> Character.isDigit(c) || c == '.')
                ? rawValue + "px"
                : rawValue;
    }
}
