package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class GdxLobbyConnectionDataTest {

    @Test
    void onlyTheHostCanCopyThePublishedLobbyEndpoint() {
        assertEquals("[CoronaPoker] localhost:2345",
                GdxFrontendScreen.lobbyConnectionClipboardText(
                        true, "  localhost:2345  "));
        assertEquals("", GdxFrontendScreen.lobbyConnectionClipboardText(
                false, "localhost:2345"));
        assertEquals("", GdxFrontendScreen.lobbyConnectionClipboardText(
                true, "  "));
    }

    @Test
    void generatedLobbyPasswordMatchesTheStrongSwingContract() {
        String password = GdxFrontendScreen.strongLobbyPassword(
                new SecureRandom());
        assertEquals(14, password.length());
        assertTrue(password.matches("[a-z0-9]{14}"));
    }
}
