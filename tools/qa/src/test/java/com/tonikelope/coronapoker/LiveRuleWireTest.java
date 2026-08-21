package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LiveRuleWireTest {

    @Test
    public void booleanRulesUseOnlyZeroOrOne() {
        assertTrue(LiveRuleWire.parseBoolean(
                new String[]{"GAME", "1", "RUNITWICERULE", "1"}, "RUNITWICERULE"));
        assertFalse(LiveRuleWire.parseBoolean(
                new String[]{"GAME", "2", "BOTBALRULE", "0"}, "BOTBALRULE"));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseBoolean(
                new String[]{"GAME", "3", "BOTBALRULE", "true"}, "BOTBALRULE"));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseBoolean(
                new String[]{"GAME", "3", "BOTBALRULE", "1", "ignored"}, "BOTBALRULE"));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseBoolean(
                new String[]{"GAME", "3", "BOTREBUYRULE", "1"}, "BOTBALRULE"));
    }

    @Test
    public void rabbitAndHandLimitHaveCanonicalCurrentRanges() {
        assertEquals(0, LiveRuleWire.parseRabbit(
                new String[]{"GAME", "4", "RABBITRULE", "0"}));
        assertEquals(3, LiveRuleWire.parseRabbit(
                new String[]{"GAME", "5", "RABBITRULE", "3"}));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseRabbit(
                new String[]{"GAME", "6", "RABBITRULE", "4"}));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseRabbit(
                new String[]{"GAME", "6", "RABBITRULE", "+1"}));

        assertEquals(-1, LiveRuleWire.parseMaxHands(
                new String[]{"GAME", "7", "MAXHANDS", "-1"}));
        assertEquals(100, LiveRuleWire.parseMaxHands(
                new String[]{"GAME", "8", "MAXHANDS", "100"}));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseMaxHands(
                new String[]{"GAME", "9", "MAXHANDS", "0"}));
        assertThrows(IllegalArgumentException.class, () -> LiveRuleWire.parseMaxHands(
                new String[]{"GAME", "9", "MAXHANDS", "01"}));
    }
}
