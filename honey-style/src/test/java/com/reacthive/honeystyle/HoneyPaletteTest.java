package com.reacthive.honeystyle;

import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HoneyPaletteTest {

    private static final Color MEDIUM_GREEN = new Color(0x2F, 0xAC, 0x2F);
    private static final Color ROYAL_BLUE = new Color(0x31, 0x8B, 0xFA);
    private static final Color LIGHT_GREEN_HONEY = new Color(0xE8, 0xF8, 0xE8);
    private static final Color LIGHT_GREEN_COLORS2 = new Color(0x4B, 0xC8, 0x00);

    private static HoneyPalette palette() {
        return new HoneyPalette(List.of(
                entry("secondary.mediumGreen", "colors", MEDIUM_GREEN),
                entry("primary.royalBlue", "colors", ROYAL_BLUE),
                entry("secondary.light", "palette", LIGHT_GREEN_HONEY),
                entry("secondary.light", "colors", LIGHT_GREEN_COLORS2)
        ), List.of("theme.ts"));
    }

    private static HoneyColorEntry entry(String path, String root, Color color) {
        return new HoneyColorEntry(path, root, color, CssColorParser.toHex(color), "theme.ts", null);
    }

    @Test
    public void findsByPath() {
        assertEquals(MEDIUM_GREEN, palette().find("secondary.mediumGreen").color());
        assertNull(palette().find("secondary.nope"));
        assertNull(palette().find(null));
    }

    @Test
    public void prefersTheNamedPaletteWhenPathsCollide() {
        HoneyPalette palette = palette();
        // First entry wins for a bare lookup...
        assertEquals(LIGHT_GREEN_HONEY, palette.find("secondary.light").color());
        // ...but an explicit root picks the palette that was actually referenced.
        assertEquals(LIGHT_GREEN_COLORS2, palette.find("secondary.light", "colors").color());
        assertEquals(LIGHT_GREEN_HONEY, palette.find("secondary.light", "palette").color());
    }

    @Test
    public void fallsBackToBarePathForUnknownRoot() {
        assertEquals(MEDIUM_GREEN, palette().find("secondary.mediumGreen", "palette").color());
    }

    @Test
    public void snapsToNearestToken() {
        HoneyPalette palette = palette();
        HoneyColorEntry nearest = palette.nearest(new Color(0x30, 0xAA, 0x30), null);
        assertNotNull(nearest);
        assertEquals("secondary.mediumGreen", nearest.path());

        HoneyColorEntry blue = palette.nearest(new Color(0x30, 0x8A, 0xF0), null);
        assertNotNull(blue);
        assertEquals("primary.royalBlue", blue.path());
    }

    @Test
    public void emptyPaletteHasNoMatches() {
        assertNull(HoneyPalette.EMPTY.find("primary.royalBlue"));
        assertNull(HoneyPalette.EMPTY.nearest(Color.RED, null));
    }

    @Test
    public void matchesLongestSubPathInTemplateText() {
        HoneyPalette palette = palette();

        HoneyPathMatcher.PathMatch bare =
                HoneyPathMatcher.match(palette, "secondary.mediumGreen");
        assertNotNull(bare);
        assertEquals(0, bare.offset());
        assertEquals("secondary.mediumGreen".length(), bare.length());

        // A `colors.` prefix is skipped, and the highlighted range covers only the path.
        HoneyPathMatcher.PathMatch prefixed =
                HoneyPathMatcher.match(palette, "colors.primary.royalBlue");
        assertNotNull(prefixed);
        assertEquals("colors.".length(), prefixed.offset());
        assertEquals("primary.royalBlue".length(), prefixed.length());

        assertNull(HoneyPathMatcher.match(palette, "props.theme"));
    }
}
