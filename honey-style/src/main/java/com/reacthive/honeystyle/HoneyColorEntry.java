package com.reacthive.honeystyle;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * A single resolved theme color, e.g. {@code secondary.mediumGreen -> #2FAC2F}.
 *
 * @param path       dotted path relative to the palette root, without the {@code colors} prefix
 * @param rootName   the palette object the value was declared in ({@code colors}, {@code colors2}, ...)
 * @param color      resolved color
 * @param rawValue   the literal as written in the theme file
 * @param sourceName presentable name of the file the value came from
 * @param sourceFile the declaring file, re-read on demand to locate the declaration
 */
public record HoneyColorEntry(String path,
                              String rootName,
                              Color color,
                              String rawValue,
                              String sourceName,
                              @Nullable VirtualFile sourceFile) {

    public String qualifiedPath() {
        return rootName + "." + path;
    }
}
