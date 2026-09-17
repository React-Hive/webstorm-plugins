package com.reacthive.honeystyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@State(name = "HoneyStyle", storages = @Storage("honeyStyle.xml"))
public final class HoneyStyleSettings implements PersistentStateComponent<HoneyStyleSettings.State> {

    public static final class State {
        /**
         * Master switch for every swatch this plugin contributes.
         */
        public boolean enabled = true;

        /**
         * Swatches for bare paths written inside styled/css template literals.
         */
        public boolean showInTemplates = true;

        /**
         * Case-insensitive substring a .ts path must contain to be scanned for a palette.
         */
        public String discoveryFilter = "theme";

        /**
         * Extra project-relative theme files to scan regardless of {@link #discoveryFilter}.
         */
        public List<String> extraThemeFiles = new ArrayList<>();

        /**
         * Extra color names, one {@code name = value} per line, on top of the 148 CSS names.
         */
        public List<String> customColors = new ArrayList<>();

        /**
         * JSX props that take a color, with or without the {@code $} prefix.
         */
        public List<String> colorProps = new ArrayList<>(DEFAULT_COLOR_PROPS);

        /**
         * Functions whose first string argument is a color path.
         */
        public List<String> colorFunctions = new ArrayList<>(DEFAULT_COLOR_FUNCTIONS);
    }

    /**
     * honey-style's {@code CSS_COLOR_PROPERTIES} - the props honey-layout resolves a path for.
     * Configurable because a project can wrap honey-layout with props of its own.
     */
    public static final List<String> DEFAULT_COLOR_PROPS = List.of(
            "color",
            "backgroundColor",
            "borderColor",
            "borderTopColor",
            "borderRightColor",
            "borderBottomColor",
            "borderLeftColor",
            "outlineColor",
            "textDecorationColor",
            "fill",
            "stroke");

    public static final List<String> DEFAULT_COLOR_FUNCTIONS = List.of("resolveColor");

    private final State state = new State();
    private volatile Map<String, String> cachedCustomColors;
    private volatile Set<String> cachedColorProps;
    private volatile Set<String> cachedColorFunctions;
    private final SimpleModificationTracker tracker = new SimpleModificationTracker();

    public static HoneyStyleSettings getInstance(@NotNull Project project) {
        return project.getService(HoneyStyleSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        XmlSerializerUtil.copyBean(loaded, state);
        invalidate();
    }

    private void invalidate() {
        cachedCustomColors = null;
        cachedColorProps = null;
        cachedColorFunctions = null;
        tracker.incModificationCount();
    }

    /**
     * Bumped whenever settings change, so cached palettes are rebuilt.
     */
    public @NotNull ModificationTracker getTracker() {
        return tracker;
    }

    public void update(boolean enabled,
                       boolean showInTemplates,
                       String discoveryFilter,
                       List<String> extraThemeFiles,
                       List<String> customColors,
                       List<String> colorProps,
                       List<String> colorFunctions) {
        state.enabled = enabled;
        state.showInTemplates = showInTemplates;
        state.discoveryFilter = discoveryFilter;
        state.extraThemeFiles = new ArrayList<>(extraThemeFiles);
        state.customColors = new ArrayList<>(customColors);
        state.colorProps = new ArrayList<>(colorProps);
        state.colorFunctions = new ArrayList<>(colorFunctions);
        invalidate();
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public boolean isShowInTemplates() {
        return state.showInTemplates;
    }

    public String getDiscoveryFilter() {
        return state.discoveryFilter == null || state.discoveryFilter.isBlank() ? "theme" : state.discoveryFilter;
    }

    public List<String> getExtraThemeFiles() {
        return state.extraThemeFiles == null ? List.of() : state.extraThemeFiles;
    }

    public List<String> getCustomColors() {
        return state.customColors == null ? List.of() : state.customColors;
    }

    /**
     * Parses the configured lines into name -> value. Accepts {@code name = value} or
     * {@code name: value}; the value is any CSS color the parser understands.
     */
    public List<String> getColorProps() {
        return state.colorProps == null || state.colorProps.isEmpty()
                ? DEFAULT_COLOR_PROPS
                : state.colorProps;
    }

    public List<String> getColorFunctions() {
        return state.colorFunctions == null || state.colorFunctions.isEmpty()
                ? DEFAULT_COLOR_FUNCTIONS
                : state.colorFunctions;
    }

    public @NotNull Set<String> getColorFunctionNames() {
        Set<String> cached = cachedColorFunctions;
        if (cached == null) {
            Set<String> names = new LinkedHashSet<>();
            for (String function : getColorFunctions()) {
                String trimmed = function == null ? "" : function.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
            cached = Set.copyOf(names);
            cachedColorFunctions = cached;
        }
        return cached;
    }

    /**
     * Lower-cased and {@code $}-stripped, so configuration can be written either way.
     */
    public @NotNull Set<String> getColorPropNames() {
        Set<String> cached = cachedColorProps;
        if (cached == null) {
            Set<String> names = new LinkedHashSet<>();
            for (String prop : getColorProps()) {
                String normalized = normalizeProp(prop);
                if (!normalized.isEmpty()) {
                    names.add(normalized);
                }
            }
            cached = Set.copyOf(names);
            cachedColorProps = cached;
        }
        return cached;
    }

    public static String normalizeProp(@Nullable String prop) {
        if (prop == null) {
            return "";
        }
        String trimmed = prop.trim();
        if (trimmed.startsWith("$")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public @NotNull Map<String, String> getCustomColorMap() {
        Map<String, String> cached = cachedCustomColors;
        if (cached != null) {
            return cached;
        }
        List<String> lines = getCustomColors();
        if (lines.isEmpty()) {
            cachedCustomColors = Map.of();
            return cachedCustomColors;
        }
        Map<String, String> colors = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") && !trimmed.contains("=") && !trimmed.contains(":")) {
                continue;
            }
            int separator = indexOfSeparator(trimmed);
            if (separator <= 0 || separator == trimmed.length() - 1) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(separator + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
                colors.put(name, value);
            }
        }
        cachedCustomColors = Map.copyOf(colors);
        return cachedCustomColors;
    }

    private static int indexOfSeparator(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }
}
