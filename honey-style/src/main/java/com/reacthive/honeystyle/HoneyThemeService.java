package com.reacthive.honeystyle;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Caches what the plugin reads from the project's theme files - the color palette and the
 * breakpoint keys used by {@code @honey-media}.
 *
 * <p>The cache deliberately does not depend on {@code PsiModificationTracker.MODIFICATION_COUNT}:
 * that fires on every keystroke anywhere in the project and would re-scan the file index
 * constantly. It depends on the theme files that were actually parsed, plus VFS structure changes
 * so a newly added theme file is picked up.
 */
public final class HoneyThemeService {

    private final Project project;
    private final SimpleModificationTracker reloadTracker = new SimpleModificationTracker();
    private volatile CachedValue<HoneyThemeModel> cache;

    public HoneyThemeService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull HoneyThemeService getInstance(@NotNull Project project) {
        return project.getService(HoneyThemeService.class);
    }

    /**
     * Forces the palette to be rebuilt on next access.
     */
    public void reload() {
        reloadTracker.incModificationCount();
    }

    public @NotNull HoneyPalette getPalette() {
        return getModel().palette();
    }

    public @NotNull List<HoneyBreakpoint> getBreakpoints() {
        return getModel().breakpoints();
    }

    public @NotNull HoneyThemeModel getModel() {
        HoneyStyleSettings settings = HoneyStyleSettings.getInstance(project);
        if (!settings.isEnabled()) {
            return HoneyThemeModel.EMPTY;
        }
        if (DumbService.isDumb(project)) {
            return HoneyThemeModel.EMPTY;
        }

        CachedValue<HoneyThemeModel> local = cache;
        if (local == null) {
            synchronized (this) {
                local = cache;
                if (local == null) {
                    local = CachedValuesManager.getManager(project).createCachedValue(() -> {
                        HoneyThemeIndexer.IndexResult result = HoneyThemeIndexer.build(project, settings);
                        List<Object> dependencies = new ArrayList<>(result.scannedFiles());
                        dependencies.add(settings.getTracker());
                        dependencies.add(reloadTracker);
                        dependencies.add(VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
                        dependencies.add(DumbService.getInstance(project).getModificationTracker());
                        return CachedValueProvider.Result.create(result.model(), dependencies);
                    }, false);
                    cache = local;
                }
            }
        }
        return local.getValue();
    }
}
