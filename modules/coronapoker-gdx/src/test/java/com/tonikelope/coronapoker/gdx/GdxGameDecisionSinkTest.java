package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.game.PlayerState;
import com.tonikelope.coronapoker.table.TableCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class GdxGameDecisionSinkTest {

    @Test
    void runItTwiceUsesTheNativeTimedVoteAndLiveTally() {
        List<GdxTableDialog> shown = new ArrayList<>();
        AtomicInteger selected = new AtomicInteger(
                GameDecisionSink.VOTE_PENDING);
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::add);

        GameDecisionSink.RunItTwiceHandle accepted
                = decisions.showRunItTwice(15, 2, "20", selected::set);
        GdxTableDialog dialog = shown.get(0);
        assertEquals("RUN IT TWICE", dialog.title());
        assertEquals(15, dialog.seconds());
        assertEquals("UNA VEZ", dialog.negativeLabel());
        assertEquals("DOS VECES", dialog.positiveLabel());
        accepted.updateTally(0, 1);
        assertTrue(dialog.message().contains("0 UNA VEZ"));
        assertTrue(dialog.message().contains("1 DOS VECES"));
        dialog.accept();
        assertEquals(GameDecisionSink.VOTE_RUN_IT_TWICE,
                accepted.currentVote());
        assertEquals(GameDecisionSink.VOTE_RUN_IT_TWICE, selected.get());

        GameDecisionSink.RunItTwiceHandle timedOut
                = decisions.showRunItTwice(3, 2, "20", null);
        shown.get(1).timeout();
        assertEquals(GameDecisionSink.VOTE_NORMAL, timedOut.currentVote());
    }

    @Test
    void recoveryReplaysEveryCanonicalLocalDecisionAndRejectsNodec() {
        AtomicReference<TableCommand> submitted = new AtomicReference<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), ignored -> { }, submitted::set);

        decisions.replayRecoveredAction(PlayerState.Decision.FOLD, 0d);
        assertTrue(submitted.get() instanceof TableCommand.Fold);
        decisions.replayRecoveredAction(PlayerState.Decision.CHECK, 0d);
        assertTrue(submitted.get() instanceof TableCommand.CheckOrCall);
        decisions.replayRecoveredAction(PlayerState.Decision.BET, 1.75d);
        assertEquals(1.75d, ((TableCommand.Bet) submitted.get()).amount());
        decisions.replayRecoveredAction(PlayerState.Decision.ALL_IN, 0d);
        assertTrue(submitted.get() instanceof TableCommand.AllIn);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> decisions.replayRecoveredAction(
                        PlayerState.Decision.NONE, 0d));
    }

    @Test
    void recoveryOverlayCanOnlyBeClosedByTheDealerHandle() {
        AtomicReference<GdxTableDialog> shown = new AtomicReference<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::set, ignored -> { });

        GameDecisionSink.CloseHandle handle = decisions.showRecovery();
        GdxTableDialog dialog = shown.get();
        assertEquals("RECUPERANDO TIMBA", dialog.title());
        assertTrue(dialog.isRecovery());
        assertTrue(dialog.isExternallyControlled());
        assertFalse(dialog.complete());

        handle.close();
        assertTrue(dialog.complete());
    }

    @Test
    void straddleUsesNativeTimedDecisionAndDefaultsToDecline() {
        AtomicReference<GdxTableDialog> shown = new AtomicReference<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::set);

        GameDecisionSink.StraddleHandle accepted
                = decisions.showStraddle(10, "0.4");
        GdxTableDialog dialog = shown.get();
        assertEquals(GdxTableDialog.Kind.CONFIRM, dialog.kind());
        assertEquals("STRADDLE", dialog.title());
        assertEquals("¿PONER STRADDLE DE 0.4?", dialog.message());
        assertEquals("NO", dialog.negativeLabel());
        assertEquals("PONER", dialog.positiveLabel());
        assertEquals(10, dialog.seconds());
        assertTrue(accepted.isOpen());
        accepted.accept();
        assertEquals(GameDecisionSink.POST_STRADDLE,
                accepted.decision().toCompletableFuture().join());

        GameDecisionSink.StraddleHandle timedOut
                = decisions.showStraddle(5, "0.8");
        GdxTableDialog timeoutDialog = shown.get();
        timeoutDialog.timeout();
        assertEquals(GameDecisionSink.NO_STRADDLE,
                timedOut.decision().toCompletableFuture().join());
        assertFalse(timedOut.isOpen());
    }

    @Test
    void timedPokerDecisionsFollowTheSelectedLanguage() {
        List<GdxTableDialog> shown = new ArrayList<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                new GdxGameText("en"), shown::add);

        decisions.showRunItTwice(12, 3, "4.5", null)
                .updateTally(1, 2);
        GdxTableDialog rit = shown.get(0);
        assertEquals("ONCE", rit.negativeLabel());
        assertEquals("TWICE", rit.positiveLabel());
        assertTrue(rit.message().contains("POT: 4.5"));
        assertTrue(rit.message().contains("UNANIMOUS VOTE REQUIRED (3)"));

        decisions.showStraddle(8, "0.8");
        GdxTableDialog straddle = shown.get(1);
        assertEquals("POST A 0.8 STRADDLE?", straddle.message());
        assertEquals("POST", straddle.positiveLabel());
    }

    @Test
    void initialAutomaticAndImmediateBuyinsKeepTheirDistinctContracts()
            throws Exception {
        AtomicReference<GdxTableDialog> shown = new AtomicReference<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::set);

        GameDecisionSink.RebuyHandle initial = decisions.showRebuy(
                new GameDecisionSink.RebuyRequest(false, 15, 2, 20, 10,
                        "rebuy.compra_inicial", false, true));
        GdxTableDialog initialDialog = shown.get();
        assertFalse(initialDialog.showsNegative(),
                "the mandatory initial buy-in cannot be cancelled");
        initialDialog.timeout();
        assertEquals(new GameDecisionSink.RebuyResult(true, 10),
                initial.result().toCompletableFuture().get(1, TimeUnit.SECONDS));

        GameDecisionSink.RebuyHandle automatic = decisions.showRebuy(
                new GameDecisionSink.RebuyRequest(false, 15, 2, 20, 10,
                        "rebuy.recomprar_auto", true, false));
        GdxTableDialog automaticDialog = shown.get();
        assertTrue(automaticDialog.showsNegative(),
                "automatic rebuy remains explicitly cancellable");
        automaticDialog.dismiss();
        assertEquals(new GameDecisionSink.RebuyResult(false, 10),
                automatic.result().toCompletableFuture().get(1, TimeUnit.SECONDS));

        GameDecisionSink.RebuyHandle immediate = decisions.showRebuy(
                new GameDecisionSink.RebuyRequest(true, 0, 1, 8, 4,
                        "rebuy.recomprar_3", false, false));
        GdxTableDialog immediateDialog = shown.get();
        assertTrue(immediateDialog.showsNegative());
        immediateDialog.changeAmount(1);
        immediateDialog.accept();
        assertEquals(new GameDecisionSink.RebuyResult(true,
                immediateDialog.amount()), immediate.result()
                        .toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void directGameOverMatchesSwingStaticTwoAndAHalfSecondDwell()
            throws Exception {
        AtomicReference<GdxTableDialog> shown = new AtomicReference<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::set);

        var result = decisions.showGameOver(
                new GameDecisionSink.GameOverRequest(true));
        GdxTableDialog dialog = shown.get();
        dialog.opened(10f);

        assertEquals(GdxTableDialog.Kind.GAME_OVER, dialog.kind());
        assertTrue(dialog.isGameOver());
        assertFalse(dialog.showsNegative());
        assertFalse(dialog.showsPositive());
        assertFalse(dialog.expired(12.49f));
        assertTrue(dialog.expired(12.5f));
        dialog.timeout();
        assertEquals(new GameDecisionSink.GameOverResult(false, 0),
                result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void interactiveGameOverMatchesSwingTwoStageRebuyFlow()
            throws Exception {
        List<GdxTableDialog> shown = new ArrayList<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::add);

        var result = decisions.showGameOver(
                new GameDecisionSink.GameOverRequest(false, 2, 20, 10, 10));
        assertEquals(1, shown.size());
        GdxTableDialog gameOver = shown.get(0);
        assertEquals(GdxTableDialog.Kind.GAME_OVER, gameOver.kind());
        assertEquals("GAME OVER", gameOver.title());
        assertEquals("ESPECTADOR", gameOver.negativeLabel());
        assertEquals("CONTINUAR", gameOver.positiveLabel());
        assertFalse(gameOver.hasAmount(),
                "the GAME OVER decision must not own the rebuy amount");
        assertEquals(10, gameOver.seconds());

        gameOver.accept();
        assertEquals(2, shown.size());
        assertFalse(result.toCompletableFuture().isDone());
        GdxTableDialog rebuy = shown.get(1);
        assertEquals(GdxTableDialog.Kind.REBUY, rebuy.kind());
        assertEquals(15, rebuy.seconds());
        assertFalse(rebuy.showsNegative(),
                "Swing's post-GAME-OVER rebuy cannot be cancelled");
        assertEquals(10, rebuy.amount());
        rebuy.changeAmount(1);
        rebuy.accept();

        assertEquals(new GameDecisionSink.GameOverResult(true,
                rebuy.amount()), result.toCompletableFuture()
                        .get(1, TimeUnit.SECONDS));
    }

    @Test
    void decliningOrTimingOutGameOverNeverOpensTheRebuyAmountDialog()
            throws Exception {
        List<GdxTableDialog> declinedDialogs = new ArrayList<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), declinedDialogs::add);
        var declined = decisions.showGameOver(
                new GameDecisionSink.GameOverRequest(false, 1, 10, 10, 10));
        declinedDialogs.get(0).dismiss();
        assertEquals(new GameDecisionSink.GameOverResult(false, 0),
                declined.toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(2, declinedDialogs.size());
        assertTrue(declinedDialogs.get(1).isGameOver(),
                "the final GAME OVER frame must remain available while its audio finishes");

        List<GdxTableDialog> timedDialogs = new ArrayList<>();
        GdxGameDecisionSink timedDecisions = new GdxGameDecisionSink(
                GameText.keys(), timedDialogs::add);
        var timed = timedDecisions.showGameOver(
                new GameDecisionSink.GameOverRequest(false, 1, 10, 10, 10));
        timedDialogs.get(0).timeout();
        assertEquals(new GameDecisionSink.GameOverResult(false, 0),
                timed.toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(2, timedDialogs.size());
    }

    @Test
    void spectatorDecisionWaitsForTheCanonicalFinalAudioBarrier()
            throws Exception {
        List<GdxTableDialog> shown = new ArrayList<>();
        List<GdxGameDecisionSink.GameOverAudioCue> cues = new ArrayList<>();
        CompletableFuture<Void> spectatorAudio = new CompletableFuture<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::add, ignored -> { }, cue -> {
                    cues.add(cue);
                    return cue == GdxGameDecisionSink.GameOverAudioCue.SPECTATOR
                            ? spectatorAudio
                            : CompletableFuture.completedFuture(null);
                });

        var result = decisions.showGameOver(
                new GameDecisionSink.GameOverRequest(false, 2, 20, 10, 10));
        assertEquals(List.of(GdxGameDecisionSink.GameOverAudioCue.OPEN), cues);

        shown.get(0).dismiss();
        assertEquals(2, shown.size());
        GdxTableDialog finalFrame = shown.get(1);
        assertTrue(finalFrame.isGameOver());
        assertTrue(finalFrame.isExternallyControlled());
        assertEquals(List.of(GdxGameDecisionSink.GameOverAudioCue.OPEN,
                GdxGameDecisionSink.GameOverAudioCue.SPECTATOR), cues);
        assertFalse(result.toCompletableFuture().isDone(),
                "the dealer must not overtake nocontinue.wav");

        spectatorAudio.complete(null);
        assertTrue(finalFrame.complete());
        assertEquals(new GameDecisionSink.GameOverResult(false, 0),
                result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void spectatorPathCannotBeStrandedByMissingBackendAudioCallback()
            throws Exception {
        List<GdxTableDialog> shown = new ArrayList<>();
        CompletableFuture<Void> missingBackendCallback = new CompletableFuture<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::add, ignored -> { }, cue ->
                        cue == GdxGameDecisionSink.GameOverAudioCue.SPECTATOR
                                ? missingBackendCallback
                                : CompletableFuture.completedFuture(null),
                20, TimeUnit.MILLISECONDS);

        var result = decisions.showGameOver(
                new GameDecisionSink.GameOverRequest(false, 2, 20, 10, 10));
        shown.get(0).dismiss();

        assertEquals(new GameDecisionSink.GameOverResult(false, 0),
                result.toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(2, shown.size());
        assertTrue(shown.get(1).complete(),
                "the final GAME OVER frame must release with the decision");
        assertTrue(missingBackendCallback.isDone());
    }

    @Test
    void continueStopsGameOverAudioBeforeOpeningMandatoryRebuy() {
        List<GdxTableDialog> shown = new ArrayList<>();
        CompletableFuture<Void> continueAudio = new CompletableFuture<>();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), shown::add, ignored -> { }, cue ->
                        cue == GdxGameDecisionSink.GameOverAudioCue.CONTINUE
                                ? continueAudio
                                : CompletableFuture.completedFuture(null));

        var result = decisions.showGameOver(
                new GameDecisionSink.GameOverRequest(false, 2, 20, 10, 10));
        shown.get(0).accept();
        assertEquals(1, shown.size());
        assertFalse(result.toCompletableFuture().isDone());

        continueAudio.complete(null);
        assertEquals(2, shown.size(),
                "rebuy opens only after the GAME OVER cue is stopped");
        assertEquals(GdxTableDialog.Kind.REBUY, shown.get(1).kind());
    }
}
