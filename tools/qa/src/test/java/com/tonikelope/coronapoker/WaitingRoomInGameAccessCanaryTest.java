/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/** Regression guard for keeping the waiting-room window inaccessible in-game. */
public class WaitingRoomInGameAccessCanaryTest {

    @Test
    public void gameUiHasNoWaitingRoomChatEntryOrShortcut() throws IOException {
        Path sourceDir = locateSourceDir();
        String gameFrame = Files.readString(sourceDir.resolve("GameFrame.java"));
        String helpers = Files.readString(sourceDir.resolve("Helpers.java"));
        String shortcuts = Files.readString(sourceDir.resolve("KeyboardShortcuts.java"));

        assertFalse(gameFrame.contains("chat_menu"),
                "GameFrame must not expose the waiting-room chat in its menu or actions");
        assertFalse(helpers.contains("Action chatAction"),
                "the in-game popup must not expose the waiting-room chat");
        assertFalse(shortcuts.contains("public static final String CHAT = \"CHAT\""),
                "the waiting-room chat shortcut must not be registered");
    }

    @Test
    public void waitingRoomRejectsBeingShownAfterGameStart() throws IOException {
        String source = Files.readString(locateSourceDir().resolve("WaitingRoomFrame.java"));

        assertTrue(source.contains("if (visible && partida_empezada)"),
                "WaitingRoomFrame must reject setVisible(true) after the game starts");
        assertFalse(source.contains("setAlwaysOnTop("),
                "WaitingRoomFrame must never enable always-on-top");
    }

    private static Path locateSourceDir() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            Path candidate = p.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("WaitingRoomFrame.java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No encuentro el fuente de CoronaPoker desde " + start);
    }
}
