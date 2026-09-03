package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GameSessionIdsTest {

    @Test
    void generatedIdentifiersKeepTheEstablishedWireShape() {
        String first = GameSessionIds.random();
        String second = GameSessionIds.random();

        assertEquals(GameSessionIds.LENGTH, first.length());
        assertTrue(first.chars().allMatch(value -> value >= 'a' && value <= 'z'));
        assertNotEquals(first, second);
    }
}
