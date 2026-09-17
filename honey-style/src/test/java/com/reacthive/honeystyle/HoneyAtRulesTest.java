package com.reacthive.honeystyle;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HoneyAtRulesTest {

    private static final HoneyBreakpoint SM = new HoneyBreakpoint("sm", "768");

    @Test
    public void bareNumericBreakpointGainsPxLikeTheTransformerDoes() {
        assertEquals("768px", SM.cssValue());
    }

    @Test
    public void breakpointValueWithAUnitIsLeftAlone() {
        assertEquals("48rem", new HoneyBreakpoint("sm", "48rem").cssValue());
    }

    @Test
    public void upIsMinWidthAndIsTheDefaultDirection() {
        assertEquals("min-width: 768px", HoneyAtRules.mediaFeature(SM, "up"));
        assertEquals("min-width: 768px", HoneyAtRules.mediaFeature(SM, null));
    }

    @Test
    public void downIsMaxWidthWithNoBoundaryAdjustment() {
        assertEquals("max-width: 768px", HoneyAtRules.mediaFeature(SM, "down"));
    }

    @Test
    public void splitsBreakpointTokens() {
        assertArrayEquals(new String[]{"sm", null}, HoneyAtRules.splitBreakpointToken("sm"));
        assertArrayEquals(new String[]{"sm", "up"}, HoneyAtRules.splitBreakpointToken("sm:up"));
        assertArrayEquals(new String[]{"sm", "down"}, HoneyAtRules.splitBreakpointToken("sm:DOWN"));
    }

    @Test
    public void everyRuleIsLookedUpByNameAndParamsMatchTakesParams() {
        assertNull(HoneyAtRules.byName("honey-nope"));
        assertNull(HoneyAtRules.byName(null));
        for (HoneyAtRules.AtRule rule : HoneyAtRules.ALL) {
            assertNotNull(rule.name(), HoneyAtRules.byName(rule.name()));
            assertTrue(rule.name(), rule.name().startsWith("honey-"));
            assertEquals(rule.name(), rule.takesParams(), !rule.params().isEmpty());
            assertTrue(rule.name(), !rule.expansion().isEmpty());
        }
    }
}
