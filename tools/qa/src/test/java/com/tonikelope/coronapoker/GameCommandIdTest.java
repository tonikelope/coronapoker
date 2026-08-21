/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class GameCommandIdTest {

    @Test
    void generatorNeverEmitsANegativeGameIdOrWrapsSilently() throws Exception {
        AtomicInteger sequence = sequence();
        int saved = sequence.get();
        try {
            sequence.set(0);
            assertEquals(0, GameCommandId.next());
            assertEquals(1, GameCommandId.next());

            sequence.set(Integer.MAX_VALUE - 1);
            assertEquals(Integer.MAX_VALUE - 1, GameCommandId.next());
            assertThrows(IllegalStateException.class, GameCommandId::next,
                    "exhaustion must fail before id + 1 confirmation arithmetic can overflow");
        } finally {
            sequence.set(saved);
        }
    }

    @Test
    void generatedIdPassesTheStrictFirstHandReadyBoundary() throws Exception {
        AtomicInteger sequence = sequence();
        int saved = sequence.get();
        try {
            sequence.set(0);
            int id = GameCommandId.next();
            assertTrue(Crupier.handReadyMatchesNextHand(
                    new String[]{"GAME", Integer.toString(id), "HAND_READY", "1"}, 0));
        } finally {
            sequence.set(saved);
        }
    }

    private static AtomicInteger sequence() throws Exception {
        Field field = GameCommandId.class.getDeclaredField("NEXT");
        field.setAccessible(true);
        return (AtomicInteger) field.get(null);
    }
}
