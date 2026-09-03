/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameSessionTest {

    private static final GameConfigCodecV1.Configuration CONFIGURATION
            = GameConfigCodecV1.fromSettings(
                    new com.tonikelope.coronapoker.core.NewGameTableDraft().snapshot(),
                    false, "test-session");

    @Test
    void ownsIdentityRoleLifecycleAndNeutralTable() {
        GameSession session = new GameSession("Alice", true, CONFIGURATION);

        assertEquals("Alice", session.localNickname());
        assertTrue(session.isHost());
        assertEquals("Alice", session.table().localNickname());
        assertEquals(GameSession.Phase.CREATED, session.phase());
        assertEquals(0L, session.playTimeSeconds());
        assertEquals(0L, session.startTimestampMillis());
        assertFalse(session.isRunItTwiceLocked());
        assertEquals(CONFIGURATION, session.configuration());
        assertEquals(CONFIGURATION.recover(), session.isRecovering());
        session.setRecovering(true);
        assertTrue(session.isRecovering());
        session.setStartTimestampMillis(123456789L);
        session.setRunItTwiceLocked(true);
        assertEquals(123456789L, session.startTimestampMillis());
        assertTrue(session.isRunItTwiceLocked());

        session.setIwtsth(!CONFIGURATION.iwtsth());
        session.setRunItTwice(!CONFIGURATION.runItTwice());
        session.setRabbitHunting(1);
        session.setBotRebuy(!CONFIGURATION.botRebuy());
        session.setBotBalanceToHumans(!CONFIGURATION.botBalanceToHumans());
        assertEquals(!CONFIGURATION.iwtsth(), session.configuration().iwtsth());
        assertEquals(!CONFIGURATION.runItTwice(), session.configuration().runItTwice());
        assertEquals(1, session.configuration().rabbitHunting());
        assertEquals(!CONFIGURATION.botRebuy(), session.configuration().botRebuy());
        assertEquals(!CONFIGURATION.botBalanceToHumans(),
                session.configuration().botBalanceToHumans());

        com.tonikelope.coronapoker.core.NewGameTableDraft blindDraft
                = new com.tonikelope.coronapoker.core.NewGameTableDraft();
        blindDraft.setBlindLevelIndex(1);
        blindDraft.setAnte(true);
        GameConfigCodecV1.Configuration blindUpdate = GameConfigCodecV1.fromSettings(
                blindDraft.snapshot(), false, CONFIGURATION.sessionId());
        session.applyBlindUpdate(blindUpdate);
        assertEquals(blindUpdate.smallBlind(), session.configuration().smallBlind());
        assertEquals(blindUpdate.bigBlind(), session.configuration().bigBlind());
        assertTrue(session.configuration().ante());
        assertEquals(!CONFIGURATION.iwtsth(), session.configuration().iwtsth());

        session.setHands(12);
        session.applyRecoveredBuyin(25, false);
        assertEquals(12, session.configuration().hands());
        assertEquals(25, session.configuration().buyin());
        assertFalse(session.configuration().rebuy());

        session.setPlayTimeSeconds(41L);
        assertEquals(42L, session.incrementPlayTimeSecond());
        assertThrows(IllegalArgumentException.class,
                () -> session.setPlayTimeSeconds(-1L));

        session.start();
        session.setPaused(true);
        assertEquals(GameSession.Phase.RUNNING, session.phase());
        assertTrue(session.isPaused());
        assertTrue(session.table().paused());

        session.finish();
        assertEquals(GameSession.Phase.FINISHED, session.phase());
        assertTrue(session.table().finished());

        session.close();
        assertEquals(GameSession.Phase.CLOSED, session.phase());
        assertThrows(IllegalStateException.class, () -> session.setPaused(false));
        assertThrows(IllegalStateException.class,
                () -> session.updateConfiguration(CONFIGURATION));
        assertThrows(IllegalStateException.class,
                () -> session.setRunItTwiceLocked(false));
    }

    @Test
    void clientRoleAndInvalidLifecycleRemainExplicit() {
        GameSession session = new GameSession("Bob", false);

        assertFalse(session.isHost());
        assertThrows(IllegalStateException.class, session::configuration);
        session.start();
        assertThrows(IllegalStateException.class, session::start);
    }
}
