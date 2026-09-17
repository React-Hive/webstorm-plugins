package com.reacthive.honeystyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(name = "HoneyStyle", storages = @Storage("honeyStyle.xml"))
public final class HoneyStyleSettings implements PersistentStateComponent<HoneyStyleSettings.State> {

    public static final class State {
        /** Master switch for every swatch this plugin contributes. */
        public boolean enabled = true;

        /** Swatches for bare paths written inside styled/css template literals. */
        public boolean showInTemplates = true;

        /** Case-insensitive substring a .ts path must contain to be scanned for a palette. */
        public String discoveryFilter = "theme";

        /** Extra project-relative theme files to scan regardless of {@link #discoveryFilter}. */
        public List<String> extraThemeFiles = new ArrayList<>();
    }

    private State state = new State();
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
        tracker.incModificationCount();
    }

    /** Bumped whenever settings change, so cached palettes are rebuilt. */
    public @NotNull ModificationTracker getTracker() {
        return tracker;
    }

    public void update(boolean enabled, boolean showInTemplates, String discoveryFilter, List<String> extraThemeFiles) {
        state.enabled = enabled;
        state.showInTemplates = showInTemplates;
        state.discoveryFilter = discoveryFilter;
        state.extraThemeFiles = new ArrayList<>(extraThemeFiles);
        tracker.incModificationCount();
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
}
