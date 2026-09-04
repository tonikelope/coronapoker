/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.game.PlayerState;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Native in-table decisions requested by the canonical dealer. */
final class GdxGameDecisionSink implements GameDecisionSink {

    private final GameDecisionSink fallback = GameDecisionSink.noop();
    private final GameText text;

    GdxGameDecisionSink() {
        this(GameText.keys());
    }

    GdxGameDecisionSink(GameText text) {
        this.text = Objects.requireNonNull(text, "text");
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
            GdxTableDialog finalDialog = new GdxTableDialog(
                    GdxTableDialog.Kind.INFO, "GAME OVER", "",
                    GameDialogSink.Icon.STOP, 860, 0, true, "", "CERRAR");
            CompletableFuture<GameOverResult> result = new CompletableFuture<>();
            finalDialog.result().thenAccept(ignored -> result.complete(
                    new GameOverResult(false, 0)));
            present(finalDialog);
            return result;
        }
        GdxTableDialog choice = new GdxTableDialog("GAME OVER",
                text.translate("rebuy.recompra_3"), 900,
                request.timeoutSeconds(), false, "ESPECTADOR",
                request.minimum(), request.maximum(), request.defaultAmount());
        CompletableFuture<GameOverResult> result = new CompletableFuture<>();
        choice.result().thenAccept(continuePlaying -> result.complete(
                new GameOverResult(continuePlaying,
                        continuePlaying ? choice.amount() : 0)));
        present(choice);
        return result;
    }

    @Override
    public void replayRecoveredAction(PlayerState.Decision decision,
            double amount) {
        fallback.replayRecoveredAction(decision, amount);
    }

    @Override
    public CloseHandle showRecovery() {
        return fallback.showRecovery();
    }

    private static void present(GdxTableDialog dialog) {
        GdxApplicationShell shell = GdxApplicationShell.active();
        if (shell == null) dialog.dismiss(); else shell.showDialog(dialog);
    }

    private static final class RitHandle implements RunItTwiceHandle {
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
                    "RUN IT TWICE", message(), GameDialogSink.Icon.NONE,
                    820, timeoutSeconds, false, "UNA VEZ", "DOS VECES");
            dialog.result().thenAccept(answer -> choose(answer
                    ? VOTE_RUN_IT_TWICE : VOTE_NORMAL));
            present(dialog);
        }

        @Override public int currentVote() { return vote.get(); }

        @Override
        public void updateTally(int normal, int runItTwice) {
            this.normal = Math.max(0, normal);
            this.twice = Math.max(0, runItTwice);
            dialog.message(message());
        }

        private String message() {
            return "BOTE: " + potText + "\nVOTOS: " + normal
                    + " UNA VEZ  ·  " + twice + " DOS VECES\n"
                    + "SE NECESITA UNANIMIDAD (" + totalVoters + ")";
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

    private static final class NativeStraddleHandle implements StraddleHandle {
        private final CompletableFuture<Integer> decision = new CompletableFuture<>();
        private final GdxTableDialog dialog;

        NativeStraddleHandle(int timeoutSeconds, String amountText) {
            dialog = new GdxTableDialog(GdxTableDialog.Kind.CONFIRM,
                    "STRADDLE", "¿PONER STRADDLE DE " + amountText + "?",
                    GameDialogSink.Icon.NONE, 780, timeoutSeconds, false,
                    "NO", "STRADDLE");
            dialog.result().thenAccept(answer -> decision.complete(answer
                    ? POST_STRADDLE : NO_STRADDLE));
            present(dialog);
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
                    !request.cancelAllowed(), cancelVisible ? "CANCELAR" : "",
                    request.minimum(), request.maximum(),
                    request.defaultAmount());
            dialog.result().thenAccept(accepted -> result.complete(
                    new RebuyResult(accepted, dialog.amount())));
            present(dialog);
        }

        @Override public CompletionStage<RebuyResult> result() { return result; }

        @Override
        public void close() {
            if (!result.isDone()) dialog.dismiss();
        }
    }
}
