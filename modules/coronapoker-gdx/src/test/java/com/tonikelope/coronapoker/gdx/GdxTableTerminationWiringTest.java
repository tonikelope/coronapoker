/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class GdxTableTerminationWiringTest {

    @Test
    void finalBarrierDefersPotentiallyBlockingCleanupOffTheRenderThread() {
        CompletableFuture<Void> visualBarrier = new CompletableFuture<>();
        CompletableFuture<Void> controllerBarrier = new CompletableFuture<>();
        AtomicReference<Runnable> queued = new AtomicReference<>();

        GdxTableRenderer.transferCompletionAsync(visualBarrier,
                controllerBarrier, queued::set);
        visualBarrier.complete(null);

        assertFalse(controllerBarrier.isDone());
        assertNotNull(queued.get());
        queued.get().run();
        assertTrue(controllerBarrier.isDone());
        assertFalse(controllerBarrier.isCompletedExceptionally());
    }

    @Test
    void deferredFinalBarrierPreservesFailures() {
        CompletableFuture<Void> visualBarrier = new CompletableFuture<>();
        CompletableFuture<Void> controllerBarrier = new CompletableFuture<>();
        AtomicReference<Runnable> queued = new AtomicReference<>();

        GdxTableRenderer.transferCompletionAsync(visualBarrier,
                controllerBarrier, queued::set);
        visualBarrier.completeExceptionally(new IllegalStateException("boom"));
        queued.get().run();

        assertTrue(controllerBarrier.isCompletedExceptionally());
    }

    @Test
    void acceptingExitDialogSubmitsExactlyOneCanonicalExitCommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);

        GdxTableDialog dialog = table.requestExit();

        assertNotNull(dialog);
        assertEquals(GdxTableDialog.Kind.CONFIRM, dialog.kind());
        assertEquals(0, submitted.size());

        dialog.accept();
        dialog.accept();

        assertEquals(1, submitted.size());
        assertInstanceOf(TableCommand.ExitGame.class, submitted.get(0));
    }

    @Test
    void cancellingExitDialogDoesNotSubmitAnExitCommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);

        GdxTableDialog dialog = table.requestExit();

        assertNotNull(dialog);
        dialog.dismiss();
        assertEquals(0, submitted.size());
    }

    @Test
    void repeatedExitRequestsReuseOneConfirmationAndSubmitOneCommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);

        GdxTableDialog first = table.requestExit();
        GdxTableDialog repeated = table.requestExit();

        assertSame(first, repeated);
        repeated.accept();
        first.accept();
        assertEquals(1, submitted.size());
        assertInstanceOf(TableCommand.ExitGame.class, submitted.get(0));
    }

    @Test
    void nativeWindowCloseUsesTheSameExitConfirmation() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);

        table.requestWindowClose();
        GdxTableDialog dialog = table.requestExit();

        assertNotNull(dialog);
        assertEquals(GdxTableDialog.Kind.CONFIRM, dialog.kind());
        assertEquals(0, submitted.size());
        dialog.accept();
        assertEquals(1, submitted.size());
        assertInstanceOf(TableCommand.ExitGame.class, submitted.get(0));
    }

    @Test
    void nativeWindowCloseFromFinalSummaryReleasesItWithoutSecondExitCommand() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        CompletableFuture<Void> finalBarrier = new CompletableFuture<>();
        TableSessionSummary summary = new TableSessionSummary("local", 1,
                20L, 1L, TableSessionSummary.CloseReason.COMPLETED,
                List.of(new TableSessionSummary.PlayerBalance(
                        "local", 10d, 10d, 0)));
        table.acceptEvent(new TableVisualEvent.CloseTable(2L, summary,
                TableSnapshot.Street.FINISHED), finalBarrier);

        table.requestWindowClose();
        assertTrue(table.resolveActiveDialogChoice(true));

        assertTrue(finalBarrier.isDone());
        assertTrue(table.finalApplicationExitRequested());
        assertEquals(0, submitted.size());
    }

    @Test
    void finalSummaryMainMenuButtonReleasesTheTerminalBarrierWithoutPokerInput() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        CompletableFuture<Void> finalBarrier = openFinalSummary(table);

        assertTrue(table.handleFinalSummaryNavigationAction(0));

        assertTrue(finalBarrier.isDone());
        assertFalse(table.finalContinueRequested());
        assertEquals(0, submitted.size());
    }

    @Test
    void finalSummaryContinueButtonRequestsReconnectWithoutPokerInput() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        CompletableFuture<Void> finalBarrier = openFinalSummary(table);

        assertTrue(table.handleFinalSummaryNavigationAction(3));

        assertTrue(finalBarrier.isDone());
        assertTrue(table.finalContinueRequested());
        assertEquals(0, submitted.size());
    }

    @Test
    void finalSummaryStatisticsButtonReleasesBarrierAndRequestsNativeStats() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        CompletableFuture<Void> finalBarrier = openFinalSummary(table);

        assertTrue(table.handleFinalSummaryNavigationAction(2));

        assertTrue(finalBarrier.isDone());
        assertTrue(table.finalStatsRequested());
        assertFalse(table.finalContinueRequested());
        assertEquals(0, submitted.size());
    }

    @Test
    void exitDialogConsumesRawInputInsteadOfLeakingThroughTheTable() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        table.requestExit();

        assertEquals(true, table.inputProcessor().touchDown(
                10, 10, 0, com.badlogic.gdx.Input.Buttons.LEFT));
        assertEquals(true, table.inputProcessor().touchUp(
                10, 10, 0, com.badlogic.gdx.Input.Buttons.LEFT));
        assertEquals(true, table.inputProcessor().scrolled(0f, 1f));
        assertEquals(0, submitted.size());
    }

    @Test
    void tableDialogResolverUsesTheSameCanonicalExitPathAsPointerAndKeyboard() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);

        table.requestExit();
        assertEquals(true, table.resolveActiveDialogChoice(true));

        assertEquals(1, submitted.size());
        assertInstanceOf(TableCommand.ExitGame.class, submitted.get(0));
    }

    @Test
    void externallyControlledDialogCannotBeDismissedByTableInput() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        GdxTableDialog recovery = GdxTableDialog.recovery();
        table.showDialog(recovery);

        assertFalse(table.resolveActiveDialogChoice(false));
        assertFalse(table.resolveActiveDialogChoice(true));
        assertFalse(recovery.complete());
        assertEquals(0, submitted.size());
    }

    @Test
    void deferredInitialBuyinRemainsPresentedUntilDealerRelease() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        GdxTableDialog rebuy = new GdxTableDialog("COMPRA INICIAL", "", 0,
                15, true, "", 2, 20, 10);
        rebuy.deferCloseAfterDecision("Esperando al resto de jugadores...");
        table.showDialog(rebuy);

        assertFalse(table.resolveActiveDialogChoice(false),
                "ESC cannot bypass a mandatory initial buy-in");
        assertFalse(rebuy.complete());
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();
        assertTrue(table.hasActiveDialog(),
                "the decided buy-in must cover the wait for the other players");
        assertFalse(table.resolveActiveDialogChoice(false),
                "the waiting modal cannot be decided a second time");

        rebuy.releaseExternalClose();
        table.advanceDialogState();
        assertFalse(table.hasActiveDialog());
        assertEquals(0, submitted.size());
    }

    @Test
    void escapeLikeResolutionClosesInformationWithoutANegativeButton() {
        ArrayList<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = table(submitted);
        GdxTableDialog information = new GdxTableDialog(
                GdxTableDialog.Kind.INFO, "INFORMACIÓN",
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.NONE,
                760, 0);
        table.showDialog(information);

        assertEquals(true, table.resolveActiveDialogChoice(false));
        assertEquals(true, information.complete());
        assertEquals(false, information.result().toCompletableFuture().join());
        assertEquals(0, submitted.size());
    }

    private static CoronaPokerGdxTable table(List<TableCommand> submitted) {
        TableSnapshot snapshot = new TableSnapshot(1L, "local",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("local"), player("remote")), List.of());
        return new CoronaPokerGdxTable(240,
                new GdxTableViewState(snapshot), submitted::add, () -> { },
                new GdxGameLogSink(), null);
    }

    private static CompletableFuture<Void> openFinalSummary(
            CoronaPokerGdxTable table) {
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        TableSessionSummary summary = new TableSessionSummary("local", 1,
                20L, 1L, TableSessionSummary.CloseReason.COMPLETED,
                List.of(new TableSessionSummary.PlayerBalance(
                        "local", 10d, 10d, 0)));
        table.acceptEvent(new TableVisualEvent.CloseTable(2L, summary,
                TableSnapshot.Street.FINISHED), barrier);
        return barrier;
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname) {
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                true, false, false, false, -1, -1, 0, 0L, false,
                TableSnapshot.Position.NONE, "", "", List.of());
    }
}
