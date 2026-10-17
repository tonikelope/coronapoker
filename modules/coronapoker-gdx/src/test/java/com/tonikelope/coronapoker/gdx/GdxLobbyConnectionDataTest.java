package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void lobbyShowsThePublicPortAndBracketsIpv6Addresses() {
        assertEquals("83.39.59.168:21339",
                GdxFrontendScreen.lobbyPublicAddressEndpoint(
                        "localhost:21339", "83.39.59.168"));
        assertEquals("[2001:db8::1]:21339",
                GdxFrontendScreen.lobbyPublicAddressEndpoint(
                        "localhost:21339", "2001:db8::1"));
        assertEquals("", GdxFrontendScreen.lobbyPublicAddressEndpoint(
                "localhost", "83.39.59.168"));
    }

    @Test
    void upnpBadgeAndServerAddressOwnSeparateLayoutLanes() {
        GdxFrontendScreen.LobbyConnectionRowLayout layout =
                GdxFrontendScreen.lobbyConnectionRowLayout(
                        GdxFrontendScreen.LOBBY_LEFT_CONTENT_X,
                        GdxFrontendScreen.LOBBY_LEFT_CONTENT_WIDTH, true);

        assertTrue(layout.statusVisible());
        assertTrue(layout.valueWidth() >= 140f);
        assertTrue(layout.valueX() + layout.valueWidth()
                + GdxFrontendScreen.LOBBY_CONNECTION_STATUS_GAP
                <= layout.statusX());
        assertTrue(layout.statusX() + layout.statusWidth()
                <= GdxFrontendScreen.LOBBY_LEFT_CONTENT_X
                        + GdxFrontendScreen.LOBBY_LEFT_CONTENT_WIDTH
                        - GdxFrontendScreen.LOBBY_CONNECTION_RIGHT_PADDING);

        GdxFrontendScreen.LobbyConnectionRowLayout noBadge =
                GdxFrontendScreen.lobbyConnectionRowLayout(
                        GdxFrontendScreen.LOBBY_LEFT_CONTENT_X,
                        GdxFrontendScreen.LOBBY_LEFT_CONTENT_WIDTH, false);
        assertFalse(noBadge.statusVisible());
        assertEquals(noBadge.fullValueWidth(), noBadge.valueWidth());
    }
}
