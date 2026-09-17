package com.reacthive.honeystyle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Finds the longest sub-path of a dotted token that exists in the palette, so both
 * {@code secondary.mediumGreen} and {@code colors.secondary.mediumGreen} resolve.
 */
public final class HoneyPathMatcher {

    /**
     * @param offset start of the matched path within the token
     * @param length length of the matched path
     */
    public record PathMatch(HoneyColorEntry entry, int offset, int length) {

        public boolean contains(int offsetInToken) {
            return offsetInToken >= offset && offsetInToken <= offset + length;
        }
    }

    private HoneyPathMatcher() {
    }

    public static @Nullable PathMatch match(@NotNull HoneyPalette palette, @NotNull String token) {
        String[] segments = token.split("\\.");
        int count = segments.length;
        for (int length = count; length >= 2; length--) {
            for (int start = 0; start + length <= count; start++) {
                String candidate = String.join(".", List.of(segments).subList(start, start + length));
                String root = start > 0 ? segments[start - 1] : null;
                HoneyColorEntry entry = palette.find(candidate, root);
                if (entry != null) {
                    return new PathMatch(entry, charOffset(segments, start), candidate.length());
                }
            }
        }
        return null;
    }

    private static int charOffset(String[] segments, int segmentIndex) {
        int offset = 0;
        for (int i = 0; i < segmentIndex; i++) {
            offset += segments[i].length() + 1;
        }
        return offset;
    }
}
