/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.table.TableCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameFrameTableCommandRoutingTest {

    @Test
    void gameplayCommandsReuseTheCanonicalShortcutActions() {
        assertEquals(KeyboardShortcuts.FOLD,
                GameFrame.tableActionId(new TableCommand.Fold()));
        assertEquals(KeyboardShortcuts.CHECK,
                GameFrame.tableActionId(new TableCommand.CheckOrCall()));
        assertEquals(KeyboardShortcuts.CHECK,
                GameFrame.tableActionId(new TableCommand.ShowCards()));
        assertEquals(KeyboardShortcuts.BET,
                GameFrame.tableActionId(new TableCommand.Bet(300)));
        assertEquals(KeyboardShortcuts.ALLIN,
                GameFrame.tableActionId(new TableCommand.AllIn()));
    }

    @Test
    void lifecycleCommandsReuseTheCanonicalGameActions() {
        assertEquals(KeyboardShortcuts.QUIT,
                GameFrame.tableActionId(new TableCommand.ExitGame()));
        assertEquals(KeyboardShortcuts.PAUSE,
                GameFrame.tableActionId(new TableCommand.TogglePause()));
        assertEquals(KeyboardShortcuts.LOG_REGISTRO,
                GameFrame.tableActionId(new TableCommand.OpenLog()));
        assertNull(GameFrame.tableActionId(new TableCommand.ChangeDeck()));
        assertNull(GameFrame.tableActionId(new TableCommand.OpenSettings()));
    }
}
