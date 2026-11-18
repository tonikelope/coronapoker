/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.audio.Sound;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Proxy;
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
    void lastHandSwitchLeavesFeedbackToItsAuthoritativeEvent()
            throws ReflectiveOperationException {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted, hostLobby(true));
        List<String> genericCues = new ArrayList<>();
        for (String cue : List.of("buttonOnSound", "buttonOffSound")) {
            var field = CoronaPokerGdxTable.class.getDeclaredField(cue);
            field.setAccessible(true);
            field.set(table, Proxy.newProxyInstance(Sound.class.getClassLoader(),
                    new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                        if (!method.getName().equals("play")) return null;
                        genericCues.add(cue);
                        return 1L;
                    }));
        }
        var request = CoronaPokerGdxTable.class.getDeclaredMethod(
                "requestLastHandChange");
        request.setAccessible(true);

        request.invoke(table);
        assertTrue(table.resolveActiveDialogChoice(false));
        table.advanceDialogState();
        assertTrue(submitted.isEmpty());

        request.invoke(table);
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertEquals(List.of(new TableCommand.SetLastHand(true)), submitted);

        var state = CoronaPokerGdxTable.class.getDeclaredField("liveState");
        state.setAccessible(true);
        ((GdxTableViewState) state.get(table)).apply(
                new TableVisualEvent.LastHandStatus(2L, true));
        request.invoke(table);
        assertEquals(List.of(new TableCommand.SetLastHand(true),
                new TableCommand.SetLastHand(false)), submitted);
        assertTrue(genericCues.isEmpty(), genericCues::toString);
    }

    @Test
    void hostConfirmationSubmitsTimedOutRemoteKickCommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted, hostLobby(true), true);

        GdxTableDialog dialog = table.requestKickTimedOutPlayer("remote");

        assertNotNull(dialog);
        assertEquals(GdxTableDialog.Kind.CONFIRM, dialog.kind());
        assertEquals(0, submitted.size());
        dialog.accept();
        assertEquals(1, submitted.size());
        TableCommand.KickTimedOutPlayer command = assertInstanceOf(
                TableCommand.KickTimedOutPlayer.class, submitted.get(0));
        assertEquals("remote", command.nickname());
    }

    @Test
    void timedOutKickIsUnavailableForHealthyRemoteOrLocalPlayer() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable healthy = table(submitted, hostLobby(true));
        CoronaPokerGdxTable timedOut = table(submitted, hostLobby(true), true);

        assertNull(healthy.requestKickTimedOutPlayer("remote"));
        assertNull(timedOut.requestKickTimedOutPlayer("local"));
        assertEquals(0, submitted.size());
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
        return table(submitted, lobby, false);
    }

    private static CoronaPokerGdxTable table(List<TableCommand> submitted,
            LobbySession lobby, boolean remoteTimedOut) {
        TableSnapshot snapshot = new TableSnapshot(1L, "local",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("local", false),
                        player("remote", remoteTimedOut)), List.of());
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
                List.of(), null, false);
    }

    private static LobbySession lobby(boolean host,
            List<LobbyParticipant> participants) {
        LobbySnapshot snapshot = new LobbySnapshot("local", "remote",
                "localhost:2345", host, LobbySnapshot.Phase.IN_GAME, "",
                participants, List.of(), null, false);
        return new LobbySession(snapshot,
                command -> CompletableFuture.completedFuture(null));
    }

    private static LobbyParticipant participant(String nickname,
            boolean local, boolean host, boolean connected) {
        return new LobbyParticipant(nickname, null, local, host, false,
                connected, false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            boolean timedOut) {
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                true, false, false, timedOut, -1, -1, 0, 0L, false,
                TableSnapshot.Position.NONE, "", "", List.of());
    }
}
