/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GdxTableHostMaintenanceWiringTest {

    @Test
    void hostConfirmationSubmitsExactlyOneForceReconnectCommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted, hostLobby(true));

        GdxTableDialog first = table.requestForceReconnect();
        GdxTableDialog repeated = table.requestForceReconnect();

        assertNotNull(first);
        assertSame(first, repeated);
        assertEquals(GdxTableDialog.Kind.CONFIRM, first.kind());
        assertEquals(0, submitted.size());

        first.accept();
        first.accept();

        assertEquals(1, submitted.size());
        assertInstanceOf(TableCommand.ForceReconnectPlayers.class,
                submitted.get(0));
    }

    @Test
    void cancellingForceReconnectDoesNotSubmitACommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted, hostLobby(true));

        GdxTableDialog dialog = table.requestForceReconnect();

        assertNotNull(dialog);
        dialog.dismiss();
        assertEquals(0, submitted.size());
    }

    @Test
    void forceReconnectIsUnavailableWithoutConnectedRemoteHuman() {
        ArrayList<TableCommand> submitted = new ArrayList<>();

        assertNull(table(submitted, hostLobby(false)).requestForceReconnect());
        assertEquals(0, submitted.size());
    }

    @Test
    void forceReconnectIsUnavailableToClient() {
        ArrayList<TableCommand> submitted = new ArrayList<>();

        assertNull(table(submitted, clientLobby()).requestForceReconnect());
        assertEquals(0, submitted.size());
    }

    @Test
    void disconnectedRemoteHumanIsProjectedAsReconnectingWithoutChangingPokerState() {
        CoronaPokerGdxTable disconnected = table(new ArrayList<>(),
                hostLobby(false));
        CoronaPokerGdxTable connected = table(new ArrayList<>(),
                hostLobby(true));

        org.junit.jupiter.api.Assertions.assertTrue(
                disconnected.isLiveReconnectingPlayer("remote"));
        org.junit.jupiter.api.Assertions.assertFalse(
                disconnected.isLiveReconnectingPlayer("local"));
        org.junit.jupiter.api.Assertions.assertFalse(
                connected.isLiveReconnectingPlayer("remote"));
    }

    @Test
    void clientReconnectPhaseLocksOnlyTheClientTableUntilTransportRecovers() {
        LobbySession lobby = clientLobby();
        CoronaPokerGdxTable table = table(new ArrayList<>(), lobby);

        org.junit.jupiter.api.Assertions.assertFalse(
                table.isClientTransportReconnecting());
        lobby.publish(clientSnapshot(LobbySnapshot.Phase.RECONNECTING));
        org.junit.jupiter.api.Assertions.assertTrue(
                table.isClientTransportReconnecting());
        lobby.publish(clientSnapshot(LobbySnapshot.Phase.IN_GAME));
        org.junit.jupiter.api.Assertions.assertFalse(
                table.isClientTransportReconnecting());
    }

    private static CoronaPokerGdxTable table(List<TableCommand> submitted,
            LobbySession lobby) {
        TableSnapshot snapshot = new TableSnapshot(1L, "local",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("local"), player("remote")), List.of());
        return new CoronaPokerGdxTable(240,
                new GdxTableViewState(snapshot), submitted::add, () -> { },
                new GdxGameLogSink(), null, lobby);
    }

    private static LobbySession hostLobby(boolean remoteConnected) {
        return lobby(true, List.of(
                participant("local", true, true, true),
                participant("remote", false, false, remoteConnected)));
    }

    private static LobbySession clientLobby() {
        return new LobbySession(clientSnapshot(LobbySnapshot.Phase.IN_GAME),
                command -> CompletableFuture.completedFuture(null));
    }

    private static LobbySnapshot clientSnapshot(LobbySnapshot.Phase phase) {
        return new LobbySnapshot("local", "remote", "localhost:2345", false,
                phase, "", List.of(
                participant("local", true, false, true),
                participant("remote", false, true,
                        phase != LobbySnapshot.Phase.RECONNECTING)),
                List.of(), null, false, true);
    }

    private static LobbySession lobby(boolean host,
            List<LobbyParticipant> participants) {
        LobbySnapshot snapshot = new LobbySnapshot("local", "remote",
                "localhost:2345", host, LobbySnapshot.Phase.IN_GAME, "",
                participants, List.of(), null, false, true);
        return new LobbySession(snapshot,
                command -> CompletableFuture.completedFuture(null));
    }

    private static LobbyParticipant participant(String nickname,
            boolean local, boolean host, boolean connected) {
        return new LobbyParticipant(nickname, null, local, host, false,
                connected, false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname) {
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                true, false, false, false, -1, -1, 0, 0L, false,
                TableSnapshot.Position.NONE, "", "", List.of());
    }
}
