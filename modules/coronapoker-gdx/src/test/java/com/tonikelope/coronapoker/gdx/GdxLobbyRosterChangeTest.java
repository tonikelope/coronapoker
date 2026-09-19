package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdxLobbyRosterChangeTest {

    @Test
    void detectsJoinsLeavesAndAtomicReplacementWithoutInitialFalseCue() {
        LobbySnapshot hostOnly = snapshot();
        LobbySnapshot withFirstBot = snapshot(bot("CoronaBot$1"));
        LobbySnapshot withSecondBot = snapshot(bot("CoronaBot$2"));

        assertEquals(new GdxFrontendScreen.LobbyRosterChange(false, false),
                GdxFrontendScreen.lobbyRosterChange(null, hostOnly));
        assertEquals(new GdxFrontendScreen.LobbyRosterChange(true, false),
                GdxFrontendScreen.lobbyRosterChange(hostOnly, withFirstBot));
        assertEquals(new GdxFrontendScreen.LobbyRosterChange(false, true),
                GdxFrontendScreen.lobbyRosterChange(withFirstBot, hostOnly));
        assertEquals(new GdxFrontendScreen.LobbyRosterChange(true, true),
                GdxFrontendScreen.lobbyRosterChange(withFirstBot,
                        withSecondBot));
        assertEquals(new GdxFrontendScreen.LobbyRosterChange(false, false),
                GdxFrontendScreen.lobbyRosterChange(withFirstBot,
                        withFirstBot));
    }

    private static LobbySnapshot snapshot(LobbyParticipant... remote) {
        var participants = new java.util.ArrayList<LobbyParticipant>();
        participants.add(new LobbyParticipant("server", null, true, true,
                false, true, false, true,
                LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY));
        participants.addAll(List.of(remote));
        return new LobbySnapshot("server", "server", "localhost:2345", true,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "", participants,
                List.of(), null, false, true);
    }

    private static LobbyParticipant bot(String nickname) {
        return new LobbyParticipant(nickname, null, false, false, true,
                true, false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
    }
}
