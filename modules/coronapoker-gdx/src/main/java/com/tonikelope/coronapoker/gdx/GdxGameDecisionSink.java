/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameTiming;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.game.PlayerState;
import com.tonikelope.coronapoker.table.TableCommand;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Native in-table decisions requested by the canonical dealer. */
final class GdxGameDecisionSink implements GameDecisionSink {

    private static final long GAME_OVER_AUDIO_SAFETY_TIMEOUT_SECONDS = 4L;

    enum GameOverAudioCue {
        OPEN,
        CONTINUE,
        SPECTATOR
    }

    private final GameText text;
    private final Consumer<GdxTableDialog> presenter;
    private final Consumer<TableCommand> recoveredActionSubmitter;
    private final Function<GameOverAudioCue, CompletionStage<Void>>
            gameOverAudio;
    private final long gameOverAudioSafetyTimeout;
    private final TimeUnit gameOverAudioSafetyTimeoutUnit;

    GdxGameDecisionSink() {
        this(GameText.keys());
    }

    GdxGameDecisionSink(GameText text) {
        this(text, GdxGameDecisionSink::present,
                GdxGameDecisionSink::submitRecoveredAction,
                GdxGameDecisionSink::presentGameOverAudio);
    }

    GdxGameDecisionSink(GameText text,
            Consumer<GdxTableDialog> presenter) {
        this(text, presenter, GdxGameDecisionSink::submitRecoveredAction,
                GdxGameDecisionSink::presentGameOverAudio);
    }

    GdxGameDecisionSink(GameText text,
            Consumer<GdxTableDialog> presenter,
            Consumer<TableCommand> recoveredActionSubmitter) {
        this(text, presenter, recoveredActionSubmitter,
                GdxGameDecisionSink::presentGameOverAudio);
    }

    GdxGameDecisionSink(GameText text,
            Consumer<GdxTableDialog> presenter,
            Consumer<TableCommand> recoveredActionSubmitter,
            Function<GameOverAudioCue, CompletionStage<Void>> gameOverAudio) {
        this(text, presenter, recoveredActionSubmitter, gameOverAudio,
                GAME_OVER_AUDIO_SAFETY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    GdxGameDecisionSink(GameText text,
            Consumer<GdxTableDialog> presenter,
            Consumer<TableCommand> recoveredActionSubmitter,
            Function<GameOverAudioCue, CompletionStage<Void>> gameOverAudio,
            long gameOverAudioSafetyTimeout,
            TimeUnit gameOverAudioSafetyTimeoutUnit) {
        this.text = Objects.requireNonNull(text, "text");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.recoveredActionSubmitter = Objects.requireNonNull(
                recoveredActionSubmitter, "recoveredActionSubmitter");
        this.gameOverAudio = Objects.requireNonNull(gameOverAudio,
                "gameOverAudio");
        if (gameOverAudioSafetyTimeout <= 0L) {
            throw new IllegalArgumentException(
                    "game-over audio safety timeout must be positive");
        }
        this.gameOverAudioSafetyTimeout = gameOverAudioSafetyTimeout;
        this.gameOverAudioSafetyTimeoutUnit = Objects.requireNonNull(
                gameOverAudioSafetyTimeoutUnit,
                "gameOverAudioSafetyTimeoutUnit");
    }

    @Override
    public RunItTwiceHandle showRunItTwice(int timeoutSeconds,
            int totalVoters, String potText, IntConsumer voteListener) {
        return new RitHandle(timeoutSeconds, totalVoters,
                Objects.requireNonNull(potText, "potText"), voteListener);
    }

    @Override
    public StraddleHandle showStraddle(int timeoutSeconds, String amountText) {
        return new NativeStraddleHandle(timeoutSeconds,
                Objects.requireNonNull(amountText, "amountText"));
    }

    @Override
    public RebuyHandle showRebuy(RebuyRequest request) {
        return new NativeRebuyHandle(Objects.requireNonNull(request,
                "request"));
    }

    @Override
    public CompletionStage<GameOverResult> showGameOver(GameOverRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.direct()) {
            GdxTableDialog finalDialog = GdxTableDialog.gameOverFinal(2.5f);
            CompletableFuture<GameOverResult> result = new CompletableFuture<>();
            finalDialog.result().thenAccept(ignored -> result.complete(
                    new GameOverResult(false, 0)));
            presenter.accept(finalDialog);
            return result;
        }
        // Keep Swing's two-stage contract.  GAME OVER only decides whether the
        // player continues; the amount belongs to the subsequent mandatory
        // RebuyDialog.  Combining both in one modal used the wrong timeout and
        // could commit an amount before the player had actually chosen rebuy.
        GdxTableDialog choice = GdxTableDialog.gameOverChoice(
                request.timeoutSeconds());
        CompletableFuture<GameOverResult> result = new CompletableFuture<>();
        choice.result().thenAccept(continuePlaying -> {
            if (!continuePlaying) {
                GdxTableDialog finalDialog = GdxTableDialog.gameOverFinal(0f);
                presenter.accept(finalDialog);
                afterGameOverAudio(GameOverAudioCue.SPECTATOR, () -> {
                    finalDialog.dismiss();
                    result.complete(new GameOverResult(false, 0));
                });
                return;
            }
            afterGameOverAudio(GameOverAudioCue.CONTINUE, () -> {
                GdxTableDialog rebuy = new GdxTableDialog(
                        text.translate("rebuy.recomprar_3"), "", 820,
                        GameTiming.REBUY_DIALOG_COUNTDOWN_SECONDS, true, "",
                        request.minimum(), request.maximum(),
                        request.defaultAmount());
                rebuy.result().thenAccept(accepted -> result.complete(
                        new GameOverResult(true, rebuy.amount())));
                presenter.accept(rebuy);
            });
        });
        signalGameOverAudio(GameOverAudioCue.OPEN);
        presenter.accept(choice);
        return result;
    }

    @Override
    public void replayRecoveredAction(PlayerState.Decision decision,
            double amount) {
        recoveredActionSubmitter.accept(recoveredCommand(decision, amount));
    }

    @Override
    public CloseHandle showRecovery() {
        GdxTableDialog dialog = GdxTableDialog.recovery();
        presenter.accept(dialog);
        return dialog::dismiss;
    }

    private static void present(GdxTableDialog dialog) {
        GdxApplicationShell shell = GdxApplicationShell.active();
        if (shell == null) dialog.dismiss(); else shell.showDialog(dialog);
    }

    private static void submitRecoveredAction(TableCommand command) {
        GdxApplicationShell.requireActive().submitTableCommand(command);
    }

    private static CompletionStage<Void> presentGameOverAudio(
            GameOverAudioCue cue) {
        GdxApplicationShell shell = GdxApplicationShell.active();
        return shell == null ? CompletableFuture.completedFuture(null)
                : shell.playGameOverAudio(cue);
    }

    private void signalGameOverAudio(GameOverAudioCue cue) {
        try {
            CompletionStage<Void> stage = gameOverAudio.apply(cue);
            if (stage != null) {
                stage.exceptionally(failure -> null);
            }
        } catch (RuntimeException ignored) {
            // Audio is presentation-only and may never strand the dealer.
        }
    }

    private void afterGameOverAudio(GameOverAudioCue cue,
            Runnable continuation) {
        CompletionStage<Void> stage;
        try {
            stage = gameOverAudio.apply(cue);
        } catch (RuntimeException failure) {
            continuation.run();
            return;
        }
        if (stage == null) {
            continuation.run();
            return;
        }
        boundedPresentationBarrier(stage,
                gameOverAudioSafetyTimeout, gameOverAudioSafetyTimeoutUnit)
                .whenComplete((ignored, failure) -> continuation.run());
    }

    static CompletionStage<Void> boundedPresentationBarrier(
            CompletionStage<Void> stage, long timeout, TimeUnit unit) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(unit, "unit");
        if (timeout <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        // Audio is part of the presentation order, but a backend that misses a
        // completion callback must never leave the dealer and the whole table
        // blocked forever.
        return stage.toCompletableFuture().completeOnTimeout(null, timeout, unit);
    }

    static TableCommand recoveredCommand(PlayerState.Decision decision,
            double amount) {
        Objects.requireNonNull(decision, "decision");
        return switch (decision) {
            case FOLD -> new TableCommand.Fold();
            case CHECK -> new TableCommand.CheckOrCall();
            case BET -> new TableCommand.Bet(amount);
            case ALL_IN -> new TableCommand.AllIn();
            case NONE -> throw new IllegalArgumentException(
                    "Cannot replay an empty recovered decision");
        };
    }

    private final class RitHandle implements RunItTwiceHandle {
        private final AtomicInteger vote = new AtomicInteger(VOTE_PENDING);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final int totalVoters;
        private final String potText;
        private final IntConsumer voteListener;
        private final GdxTableDialog dialog;
        private volatile int normal;
        private volatile int twice;

        RitHandle(int timeoutSeconds, int totalVoters, String potText,
                IntConsumer voteListener) {
            this.totalVoters = totalVoters;
            this.potText = potText;
            this.voteListener = voteListener;
            dialog = new GdxTableDialog(GdxTableDialog.Kind.CONFIRM,
                    tr("runittwice.dialog_title", "RUN IT TWICE"), message(),
                    GameDialogSink.Icon.NONE, 820, timeoutSeconds, false,
                    tr("gdx.runittwice.once", "UNA VEZ"),
                    tr("gdx.runittwice.twice", "DOS VECES"));
            dialog.result().thenAccept(answer -> choose(answer
                    ? VOTE_RUN_IT_TWICE : VOTE_NORMAL));
            presenter.accept(dialog);
        }

        @Override public int currentVote() { return vote.get(); }

        @Override
        public void updateTally(int normal, int runItTwice) {
            this.normal = Math.max(0, normal);
            this.twice = Math.max(0, runItTwice);
            dialog.message(message());
        }

        private String message() {
            return tr("gdx.runittwice.tally",
                    "BOTE: " + potText + "\nVOTOS: " + normal
                            + " UNA VEZ  ·  " + twice + " DOS VECES\n"
                            + "SE NECESITA UNANIMIDAD (" + totalVoters + ")",
                    potText, normal, twice, totalVoters);
        }

        private void choose(int selected) {
            if (closed.get() || !vote.compareAndSet(VOTE_PENDING, selected)) return;
            if (voteListener != null) voteListener.accept(selected);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) dialog.dismiss();
        }
    }

    private final class NativeStraddleHandle implements StraddleHandle {
        private final CompletableFuture<Integer> decision = new CompletableFuture<>();
        private final GdxTableDialog dialog;

        NativeStraddleHandle(int timeoutSeconds, String amountText) {
            dialog = new GdxTableDialog(GdxTableDialog.Kind.CONFIRM,
                    tr("straddle.dialog_titulo", "STRADDLE"),
                    tr("gdx.straddle.question",
                            "¿PONER STRADDLE DE " + amountText + "?",
                            amountText), GameDialogSink.Icon.NONE, 780,
                    timeoutSeconds, false,
                    tr("straddle.dialog_no", "NO"),
                    tr("straddle.dialog_poner", "PONER"));
            dialog.result().thenAccept(answer -> decision.complete(answer
                    ? POST_STRADDLE : NO_STRADDLE));
            presenter.accept(dialog);
        }

        @Override public CompletionStage<Integer> decision() { return decision; }
        @Override public boolean isOpen() { return !decision.isDone(); }
        @Override public void accept() { dialog.accept(); }
        @Override public void decline() { dialog.dismiss(); }
        @Override public void refreshLayout() { }
    }

    private final class NativeRebuyHandle implements RebuyHandle {
        private final CompletableFuture<RebuyResult> result =
                new CompletableFuture<>();
        private final GdxTableDialog dialog;

        NativeRebuyHandle(RebuyRequest request) {
            boolean cancelVisible = request.cancelAllowed()
                    || request.automatic();
            dialog = new GdxTableDialog(text.translate(request.headerKey()),
                    "", 820, request.timeoutSeconds(),
                    !request.cancelAllowed(), cancelVisible
                            ? tr("ui.cancelar", "CANCELAR") : "",
                    request.minimum(), request.maximum(),
                    request.defaultAmount());
            dialog.result().thenAccept(accepted -> result.complete(
                    new RebuyResult(accepted, dialog.amount())));
            presenter.accept(dialog);
        }

        @Override public CompletionStage<RebuyResult> result() { return result; }

        @Override
        public void close() {
            if (!result.isDone()) dialog.dismiss();
        }
    }

    /** Keeps key-only test doubles readable while production uses i18n. */
    private String tr(String key, String fallback, Object... arguments) {
        String translated = text.translate(key, arguments);
        return key.equals(translated) ? fallback : translated;
    }
}
