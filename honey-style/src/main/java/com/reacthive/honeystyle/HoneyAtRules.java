package com.reacthive.honeystyle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The honey-style at-rule surface, shared by completion and quick documentation.
 *
 * <p>Rule names are part of the library's API and are versioned with it, so they live here.
 * Breakpoints are project configuration and are read from {@code theme.breakpoints} instead.
 */
public final class HoneyAtRules {

    /**
     * @param name        rule name without the leading {@code @}
     * @param takesParams whether the rule reads parameters from its parentheses
     * @param params      short description of accepted parameters, empty when there are none
     * @param expansion   what the rule compiles to
     */
    public record AtRule(@NotNull String name,
                         boolean takesParams,
                         @NotNull String params,
                         @NotNull String expansion) {
    }

    public static final List<AtRule> ALL = List.of(
            new AtRule("honey-media", true,
                    "breakpoint[:up|:down], orientation, media type",
                    "a media query built from theme.breakpoints"),
            new AtRule("honey-stack", true, "gap (optional)",
                    "display: flex; flex-direction: column; gap"),
            new AtRule("honey-inline", true, "gap (optional)",
                    "display: flex; gap"),
            new AtRule("honey-center", true, "horizontal | vertical | (empty)",
                    "centers content on one or both axes"),
            new AtRule("honey-if", true, "true | false",
                    "keeps or drops the block"),
            new AtRule("honey-ellipsis", false, "",
                    "overflow: hidden; text-overflow: ellipsis"),
            new AtRule("honey-absolute-fill", false, "",
                    "position: absolute; inset: 0"));

    public static final List<String> MEDIA_ORIENTATIONS = List.of("portrait", "landscape");
    public static final List<String> MEDIA_TYPES = List.of("all", "print", "screen", "speech");
    public static final List<String> BREAKPOINT_DIRECTIONS = List.of("up", "down");
    public static final List<String> CENTER_AXES = List.of("horizontal", "vertical");
    public static final List<String> IF_VALUES = List.of("true", "false");

    private HoneyAtRules() {
    }

    public static @Nullable AtRule byName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (AtRule rule : ALL) {
            if (rule.name().equals(name)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * The media feature a breakpoint token compiles to. {@code up} is the default direction, and
     * neither direction adjusts the boundary - honey-style emits the raw value for both.
     */
    public static @NotNull String mediaFeature(@NotNull HoneyBreakpoint breakpoint, @Nullable String direction) {
        String property = "down".equals(direction) ? "max-width" : "min-width";
        return property + ": " + breakpoint.cssValue();
    }

    /** Splits {@code sm:up} into its breakpoint key and direction. */
    public static String @NotNull [] splitBreakpointToken(@NotNull String token) {
        int colon = token.indexOf(':');
        return colon < 0
                ? new String[]{token, null}
                : new String[]{token.substring(0, colon), token.substring(colon + 1).toLowerCase(Locale.ROOT)};
    }
}
