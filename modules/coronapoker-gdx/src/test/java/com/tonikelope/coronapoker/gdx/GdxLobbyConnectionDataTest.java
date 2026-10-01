package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class GdxLobbyConnectionDataTest {

    @Test
    void onlyTheHostCanCopyBothPublishedLobbyEndpoints() {
        assertEquals("[CoronaPoker] localhost:2345"
                + System.lineSeparator()
                + "[CoronaPoker] 83.39.59.168:2345",
                GdxFrontendScreen.lobbyConnectionClipboardText(
                        true, "  localhost:2345  ", "83.39.59.168"));
        assertEquals("[CoronaPoker] localhost:2345"
                + System.lineSeparator()
                + "[CoronaPoker] [2001:db8::1]:2345",
                GdxFrontendScreen.lobbyConnectionClipboardText(
                        true, "localhost:2345", "2001:db8::1"));
        assertEquals("[CoronaPoker] localhost:2345",
                GdxFrontendScreen.lobbyConnectionClipboardText(
                        true, "localhost:2345", ""));
        assertEquals("", GdxFrontendScreen.lobbyConnectionClipboardText(
                false, "localhost:2345", "83.39.59.168"));
        assertEquals("", GdxFrontendScreen.lobbyConnectionClipboardText(
                true, "  ", "83.39.59.168"));
    }

    @Test
    void generatedLobbyPasswordMatchesTheStrongSwingContract() {
        String password = GdxFrontendScreen.strongLobbyPassword(
                new SecureRandom());
        assertEquals(14, password.length());
        assertTrue(password.matches("[a-z0-9]{14}"));
    }

    @Test
    void publicAddressResponsesAreTrimmedAndMustContainAUsableIpAddress() {
        assertEquals("83.39.59.168",
                GdxFrontendScreen.normalizePublicAddress(
                        "  83.39.59.168\n"));
        assertEquals("2001:db8::1",
                GdxFrontendScreen.normalizePublicAddress("2001:db8::1"));
        assertEquals("", GdxFrontendScreen.normalizePublicAddress(
                "<html>not an address</html>"));
        assertEquals("", GdxFrontendScreen.normalizePublicAddress(
                "192.168.1.20"));
        assertEquals("", GdxFrontendScreen.normalizePublicAddress(
                "127.0.0.1"));
    }
}
