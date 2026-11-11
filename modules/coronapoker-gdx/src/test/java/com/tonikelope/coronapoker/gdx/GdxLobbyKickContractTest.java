/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLobbyKickContractTest {

    @Test
    void onlyTheHostSeesTheCloseButtonOnRemoteParticipantRows() {
        LobbyParticipant host = player("Host", true, true, false, 1);
        LobbyParticipant guest = player("Guest", false, false, false, 2);
        LobbyParticipant bot = player("CoronaBot$1", false, false, true, 0);
        LobbySnapshot hostView = snapshot(host, host, guest, bot);
        assertFalse(GdxFrontendScreen.lobbyShowsKickButton(hostView, host));
        assertTrue(GdxFrontendScreen.lobbyShowsKickButton(hostView, guest));
        assertTrue(GdxFrontendScreen.lobbyShowsKickButton(hostView, bot));

        LobbyParticipant remoteHost = player("Host", false, true, false, 1);
        LobbyParticipant localGuest = player("Guest", true, false, false, 2);
        LobbySnapshot guestView = snapshot(localGuest, remoteHost,
                localGuest, bot);
        assertFalse(GdxFrontendScreen.lobbyShowsKickButton(guestView, bot));
    }

    @Test
    void humanConfirmationIsBoundToTheParticipantStillInTheRoom() {
        LobbyParticipant host = player("Host", true, true, false, 1);
        LobbyParticipant guest = player("Guest", false, false, false, 2);
        LobbyParticipant sameGuest = player("Guest", false, false, false, 2);
        LobbyParticipant replacement = player("Guest", false, false, false, 3);
        assertTrue(GdxFrontendScreen.lobbyHasKickTarget(
                snapshot(host, host, sameGuest), guest));
        assertFalse(GdxFrontendScreen.lobbyHasKickTarget(
                snapshot(host, host, replacement), guest));
        assertFalse(GdxFrontendScreen.lobbyHasKickTarget(
                snapshot(host, host), guest));
    }

    private static LobbyParticipant player(String nick, boolean local,
            boolean host, boolean bot, int identity) {
        return new LobbyParticipant(nick, null, local, host, bot, true,
                false, true, -2, -2, new byte[]{(byte) identity});
    }

    private static LobbySnapshot snapshot(LobbyParticipant local,
            LobbyParticipant... participants) {
        return new LobbySnapshot(local.nickname(), "Host", "localhost:2345",
                local.host(), LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(participants), List.of(), null, false);
    }
}
