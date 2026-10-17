/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxLobbyKickContractTest {

    @Test
    void waitingRoomKickIsImmediateAndHasNoConfirmationState() throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"));
        int start = source.indexOf(
                "private void requestKickSelectedParticipant()");
        int end = source.indexOf("private void kickParticipant", start);
        String requestMethod = source.substring(start, end);

        assertTrue(requestMethod.contains(
                "kickParticipant(target.nickname());"));
        assertFalse(requestMethod.contains("lobbyConfirmation"));
        assertFalse(source.contains("LobbyConfirmation.KICK"));
    }
}
