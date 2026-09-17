package com.reacthive.honeystyle;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HoneyAtRuleParsingTest {

    @Test
    public void detectsAtRuleNameBeingTyped() {
        assertEquals("", HoneyAtRuleCompletionContributor.atRuleNamePrefix("  color: red;\n  @"));
        assertEquals("honey-med", HoneyAtRuleCompletionContributor.atRuleNamePrefix("@honey-med"));
        assertEquals("media", HoneyAtRuleCompletionContributor.atRuleNamePrefix("  @media"));
    }

    @Test
    public void ignoresPositionsThatAreNotAnAtRuleName() {
        assertNull(HoneyAtRuleCompletionContributor.atRuleNamePrefix("color: re"));
        assertNull(HoneyAtRuleCompletionContributor.atRuleNamePrefix("@honey-media (sm) {\n  color: "));
    }

    @Test
    public void detectsParametersOfTheRuleBeingTyped() {
        assertArrayEquals(new String[]{"honey-media", ""},
                HoneyAtRuleCompletionContributor.ruleParams("@honey-media ("));
        assertArrayEquals(new String[]{"honey-media", "sm:up "},
                HoneyAtRuleCompletionContributor.ruleParams("@honey-media (sm:up "));
        assertArrayEquals(new String[]{"honey-center", "hori"},
                HoneyAtRuleCompletionContributor.ruleParams("@honey-center(hori"));
    }

    @Test
    public void closedParensAreNotAParameterPosition() {
        assertNull(HoneyAtRuleCompletionContributor.ruleParams("@honey-media (sm:up) {\n  color: red;"));
    }

    @Test
    public void completesTheTokenUnderTheCaretNotTheWholeParams() {
        assertEquals("", HoneyAtRuleCompletionContributor.currentToken(""));
        assertEquals("sm", HoneyAtRuleCompletionContributor.currentToken("sm"));
        assertEquals("land", HoneyAtRuleCompletionContributor.currentToken("sm:up land"));
        assertEquals("", HoneyAtRuleCompletionContributor.currentToken("sm:up "));
    }
}
