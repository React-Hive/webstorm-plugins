package com.reacthive.honeystyle;

import org.junit.Test;

import java.awt.Color;
import java.util.Map;

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
    public void knowsEveryStandardCssName() {
        // royalblue is the one that made WebStorm's own swatch look like a duplicate.
        assertEquals(new Color(0x41, 0x69, 0xE1), CssColorParser.parse("royalblue"));
        assertEquals(new Color(0x66, 0x33, 0x99), CssColorParser.parse("rebeccapurple"));
        assertEquals(new Color(0xFF, 0xE4, 0xE1), CssColorParser.parse("MistyRose"));
        assertEquals(new Color(0x2F, 0x4F, 0x4F), CssColorParser.parse("darkslategray"));
    }

    @Test
    public void customNamesExtendAndOverrideTheStandardOnes() {
        Map<String, String> custom = Map.of("brand", "#318BFA", "white", "#F5F6F7");
        assertEquals(new Color(0x31, 0x8B, 0xFA), CssColorParser.parse("brand", custom));
        assertEquals(new Color(0xF5, 0xF6, 0xF7), CssColorParser.parse("white", custom));
        // Unaffected names still resolve normally, and customs do not leak into the no-arg form.
        assertEquals(new Color(0x41, 0x69, 0xE1), CssColorParser.parse("royalblue", custom));
        assertEquals(new Color(0xFF, 0xFF, 0xFF), CssColorParser.parse("white"));
    }

    @Test
    public void aCustomNameCanPointAtAnyCssColor() {
        assertEquals(new Color(1, 2, 3), CssColorParser.parse("brand", Map.of("brand", "rgb(1, 2, 3)")));
        assertNull(CssColorParser.parse("brand", Map.of("brand", "not-a-color")));
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
