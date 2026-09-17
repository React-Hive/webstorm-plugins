package com.reacthive.honeystyle;

import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CssColorParserTest {

    @Test
    public void parsesSixDigitHex() {
        assertEquals(new Color(0x2F, 0xAC, 0x2F), CssColorParser.parse("#2FAC2F"));
        assertEquals(new Color(0x31, 0x8B, 0xFA), CssColorParser.parse("#318bfa"));
    }

    @Test
    public void parsesShorthandAndAlphaHex() {
        assertEquals(new Color(0xFF, 0xFF, 0xFF), CssColorParser.parse("#fff"));
        assertEquals(new Color(0x11, 0x22, 0x33, 0x44), CssColorParser.parse("#11223344"));
        assertEquals(new Color(0x11, 0x22, 0x33, 0x44), CssColorParser.parse("#1234"));
    }

    @Test
    public void parsesRgbAndRgba() {
        assertEquals(new Color(100, 116, 139), CssColorParser.parse("rgb(100, 116, 139)"));
        assertEquals(new Color(229, 57, 53, 26), CssColorParser.parse("rgba(229, 57, 53, 0.1)"));
    }

    @Test
    public void parsesHsl() {
        assertEquals(new Color(255, 0, 0), CssColorParser.parse("hsl(0, 100%, 50%)"));
        assertEquals(new Color(0, 128, 128), CssColorParser.parse("hsl(180, 100%, 25%)"));
    }

    @Test
    public void parsesNamedColorsAndTransparent() {
        assertEquals(new Color(0, 0, 0), CssColorParser.parse("black"));
        assertEquals(new Color(255, 255, 255), CssColorParser.parse("  WHITE "));
        assertEquals(0, CssColorParser.parse("transparent").getAlpha());
    }

    @Test
    public void rejectsNonColors() {
        assertNull(CssColorParser.parse("linear-gradient(180deg, #662D91 0%, #A855F7 100%)"));
        assertNull(CssColorParser.parse("var(--brand)"));
        assertNull(CssColorParser.parse("34px"));
        assertNull(CssColorParser.parse("#zzzzzz"));
        assertNull(CssColorParser.parse(""));
        assertNull(CssColorParser.parse(null));
    }

    @Test
    public void appliesAlphaLikeHexWithAlpha() {
        Color base = CssColorParser.parse("#318BFA");
        assertNotNull(base);
        assertEquals(64, CssColorParser.withAlpha(base, 0.25).getAlpha());
        assertEquals("#318BFA40", CssColorParser.toHex(CssColorParser.withAlpha(base, 0.25)));
        assertEquals("#318BFA", CssColorParser.toHex(base));
    }
}
