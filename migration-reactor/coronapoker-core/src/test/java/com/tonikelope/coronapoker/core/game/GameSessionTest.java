/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameSessionTest {

    @Test
    void ownsIdentityRoleLifecycleAndNeutralTable() {
        GameSession session = new GameSession("Alice", true);

        assertEquals("Alice", session.localNickname());
        assertTrue(session.isHost());
        assertEquals("Alice", session.table().localNickname());
        assertEquals(GameSession.Phase.CREATED, session.phase());

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
    }

    @Test
    void clientRoleAndInvalidLifecycleRemainExplicit() {
        GameSession session = new GameSession("Bob", false);

        assertFalse(session.isHost());
        session.start();
        assertThrows(IllegalStateException.class, session::start);
    }
}
