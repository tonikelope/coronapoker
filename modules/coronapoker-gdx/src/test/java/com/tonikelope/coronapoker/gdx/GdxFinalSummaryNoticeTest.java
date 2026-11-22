/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class GdxFinalSummaryNoticeTest {

    @Test
    void serverExitOpensTheLocalizedNoticeAndBlocksEverySummaryAction()
            throws Exception {
        var table = table();
        var barrier = close(table, TableSessionSummary.CloseReason.COMPLETED, true);

        assertEquals("EL SERVIDOR HA TERMINADO LA TIMBA", activeDialog(table).message());
        for (int action = 0; action < 6; action++) {
            assertFalse(table.handleFinalSummaryNavigationAction(action));
        }
        assertFalse(barrier.isDone());

        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertFalse(table.hasActiveDialog());
        assertTrue(table.handleFinalSummaryNavigationAction(0));
        assertTrue(barrier.isDone());
    }

    @Test
    void onlyUnacknowledgedNoticesSurviveTheClosingTransition() throws Exception {
        var table = table();
        var acknowledged = dialog(GdxTableDialog.Kind.INFO, "Seen before closing");
        table.showDialog(acknowledged);
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();

        var liveConfirmation = table.requestExit();
        var first = dialog(GdxTableDialog.Kind.ERROR, "Network error");
        var second = dialog(GdxTableDialog.Kind.INFO, "Session ended");
        var wait = GdxTableDialog.recovery();
        table.showDialog(first);
        table.showDialog(wait);
        table.showDialog(second);

        var barrier = close(table, TableSessionSummary.CloseReason.FAILURE, false);
        assertFalse(liveConfirmation.result().toCompletableFuture().join());
        assertTrue(wait.complete());

        assertSame(first, activeDialog(table));
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertSame(second, activeDialog(table));
        assertFalse(table.handleFinalSummaryNavigationAction(2));

        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertFalse(table.hasActiveDialog());
        assertFalse(barrier.isDone());
    }

    @Test
    void lateNoticesStayModalAndLatePokerDecisionsAreDismissed() throws Exception {
        var table = table();
        close(table, TableSessionSummary.CloseReason.COMPLETED, false);
        assertFalse(table.hasActiveDialog());

        var lateDecision = dialog(GdxTableDialog.Kind.CONFIRM, "Still playing?");
        table.showDialog(lateDecision);
        assertFalse(lateDecision.result().toCompletableFuture().join());
        assertFalse(table.hasActiveDialog());

        var lateNotice = dialog(GdxTableDialog.Kind.ZERO_TRUST, "Late terminal notice");
        table.showDialog(lateNotice);
        assertSame(lateNotice, activeDialog(table));
        assertFalse(table.handleFinalSummaryNavigationAction(3));

        table.requestWindowClose();
        assertSame(lateNotice, activeDialog(table));
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertTrue(activeDialog(table).isExitChoice());
    }

    @Test
    void recoverableStopKeepsItsExistingTransition() {
        var table = table();
        var transientConfirmation = dialog(GdxTableDialog.Kind.CONFIRM, "Still playing?");
        table.showDialog(transientConfirmation);
        close(table, TableSessionSummary.CloseReason.RECOVERABLE_STOP, false);

        assertFalse(transientConfirmation.result().toCompletableFuture().join());
        assertFalse(table.hasActiveDialog());
        assertTrue(table.finalContinueRequested());
    }

    @Test
    void aLateNoticeIsStillModalWhenReturningFromStatistics() throws Exception {
        var table = table();
        close(table, TableSessionSummary.CloseReason.COMPLETED, false);
        var stats = new AtomicBoolean();
        table.retainFinalSummary(() -> { }, () -> stats.set(true),
                () -> { }, () -> { });

        assertTrue(table.handleFinalSummaryNavigationAction(2));
        assertTrue(stats.get());
        var notice = dialog(GdxTableDialog.Kind.ERROR, "Delayed connection error");
        table.showDialog(notice);
        table.resumeRetainedFinalSummary();

        assertSame(notice, activeDialog(table));
        assertFalse(table.handleFinalSummaryNavigationAction(0));
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertTrue(table.handleFinalSummaryNavigationAction(0));
    }

    @Test
    void aTrailingReconnectSnapshotCannotBlockTheFinalSummary() {
        var snapshot = new LobbySnapshot("local", "host", "localhost:2345", false,
                LobbySnapshot.Phase.RECONNECTING, "",
                List.of(new LobbyParticipant("local", null, true, false,
                                false, true, false, true, 0, 0),
                        new LobbyParticipant("host", null, false, true,
                                false, true, false, true, 0, 0)),
                List.of(), null, false);
        try (var lobby = new LobbySession(snapshot,
                command -> CompletableFuture.completedFuture(null))) {
            var table = table(lobby);
            assertTrue(table.isClientTransportReconnecting());
            var barrier = close(table, TableSessionSummary.CloseReason.COMPLETED, false);
            assertFalse(table.isClientTransportReconnecting());
            assertFalse(table.hasActiveDialog());
            assertTrue(table.handleFinalSummaryNavigationAction(0));
            assertTrue(barrier.isDone());
        }
    }

    static CoronaPokerGdxTable table() {
        return table(null);
    }

    static CoronaPokerGdxTable table(LobbySession lobby) {
        var player = new TableSnapshot.PlayerSnapshot("local", 20d, 0d, 20d,
                true, false, false, false, -2, -2, 0, 0L, false,
                TableSnapshot.Position.DEALER, "", "", List.of());
        var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(player), List.of());
        return new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                command -> { throw new AssertionError("Unexpected game command"); },
                () -> { }, new GdxGameLogSink(), null, lobby);
    }

    static CompletableFuture<Void> close(CoronaPokerGdxTable table,
            TableSessionSummary.CloseReason reason, boolean serverEnded) {
        var summary = new TableSessionSummary("local", 1, 15L, 1_000L, reason,
                List.of(new TableSessionSummary.PlayerBalance("local", 20d, 20d, 0)));
        var barrier = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.CloseTable(2L, summary,
                TableSnapshot.Street.FINISHED, serverEnded), barrier);
        return barrier;
    }

    private static GdxTableDialog dialog(GdxTableDialog.Kind kind, String message) {
        return new GdxTableDialog(kind, message, GameDialogSink.Icon.NONE, 760, 0);
    }

    static GdxTableDialog activeDialog(CoronaPokerGdxTable table)
            throws ReflectiveOperationException {
        Field field = CoronaPokerGdxTable.class.getDeclaredField("activeDialog");
        field.setAccessible(true);
        return (GdxTableDialog) field.get(table);
    }
}
