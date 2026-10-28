package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BotNicknameTest {

    @Test
    void customNamesReceiveTheReservedMarkerExactlyOnce() {
        assertEquals("Ada$", BotNickname.custom("  Ada  "));
        assertEquals("Ada", BotNickname.displayName("Ada$"));
        assertTrue(BotNickname.isBot("Ada$"));
        assertFalse(BotNickname.isStandard("Ada$"));
        assertThrows(IllegalArgumentException.class,
                () -> BotNickname.custom("Ada$"));
    }

    @Test
    void standardNamesRemainNumericallyRecognizable() {
        assertEquals("CoronaBot$12", BotNickname.standard(12));
        assertTrue(BotNickname.isStandard("CoronaBot$12"));
        assertFalse(BotNickname.isStandard("CoronaBot$Ada"));
    }
}
