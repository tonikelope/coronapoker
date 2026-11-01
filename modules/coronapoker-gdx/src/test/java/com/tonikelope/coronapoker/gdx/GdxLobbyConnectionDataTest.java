package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.security.SecureRandom;
import java.util.List;
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

    @Test
    void gameLogShowsBothHostEndpointsButOnlyTheServerForClients() {
        GdxGameText spanish = new GdxGameText("es");
        LobbySnapshot host = new LobbySnapshot("host", "host",
                "192.168.1.20:21339", true,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(participant("host", true, true)), List.of(),
                null, false, true);
        assertEquals(List.of(
                "IP PRIVADA / PUERTO: 192.168.1.20:21339",
                "IP PÚBLICA / PUERTO: 83.39.59.168:21339"),
                GdxApplicationShell.tableConnectionEndpointLines(host,
                        "83.39.59.168", spanish));

        LobbySnapshot client = new LobbySnapshot("client", "host",
                "83.39.59.168:21339", false,
                LobbySnapshot.Phase.IN_GAME, "",
                List.of(participant("host", false, true),
                        participant("client", true, false)),
                List.of(), null, true, true);
        assertEquals(List.of("SERVIDOR IP / PUERTO: 83.39.59.168:21339"),
                GdxApplicationShell.tableConnectionEndpointLines(client,
                        "203.0.113.9", spanish));
    }

    private static LobbyParticipant participant(String nickname,
            boolean local, boolean host) {
        return new LobbyParticipant(nickname, null, local, host, false,
                true, false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
    }
}
