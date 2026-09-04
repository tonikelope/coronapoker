/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.IntConsumer;

/**
 * Interactive in-game decisions exposed without leaking frontend widgets.
 */
public interface GameDecisionSink {

    int VOTE_PENDING = -1;
    int VOTE_NORMAL = 0;
    int VOTE_RUN_IT_TWICE = 1;
    int NO_STRADDLE = 0;
    int POST_STRADDLE = 1;

    record RebuyRequest(boolean cancelAllowed, int timeoutSeconds,
            int minimum, int maximum, int defaultAmount, String headerKey,
            boolean automatic, boolean deferClose) {

        public RebuyRequest {
            Objects.requireNonNull(headerKey, "headerKey");
            if (timeoutSeconds < 0 || minimum < 0 || maximum < minimum
                    || defaultAmount < 0) {
                throw new IllegalArgumentException("invalid rebuy request");
            }
        }
    }

    record RebuyResult(boolean accepted, int amount) {
    }

    record GameOverRequest(boolean direct, int minimum, int maximum,
            int defaultAmount, int timeoutSeconds) {

        public GameOverRequest(boolean direct) {
            this(direct, 0, 0, 0, 0);
        }

        public GameOverRequest {
            if (minimum < 0 || maximum < minimum
                    || defaultAmount < minimum || defaultAmount > maximum
                    || timeoutSeconds < 0) {
                throw new IllegalArgumentException("invalid game-over request");
            }
        }
    }

    record GameOverResult(boolean continuePlaying, int rebuyAmount) {
    }

    interface CloseHandle {

        void close();
    }

    interface RebuyHandle extends CloseHandle {

        CompletionStage<RebuyResult> result();
    }

    interface RunItTwiceHandle {

        int currentVote();

        void updateTally(int normal, int runItTwice);

        void close();
    }

    interface StraddleHandle {

        CompletionStage<Integer> decision();

        boolean isOpen();

        void accept();

        void decline();

        void refreshLayout();
    }

    RunItTwiceHandle showRunItTwice(int timeoutSeconds, int totalVoters,
            String potText, IntConsumer voteListener);

    StraddleHandle showStraddle(int timeoutSeconds, String amountText);

    RebuyHandle showRebuy(RebuyRequest request);

    CompletionStage<GameOverResult> showGameOver(GameOverRequest request);

    void replayRecoveredAction(PlayerState.Decision decision, double amount);

    CloseHandle showRecovery();

    static GameDecisionSink noop() {
        return new GameDecisionSink() {
            @Override
            public RunItTwiceHandle showRunItTwice(int timeoutSeconds,
                    int totalVoters, String potText, IntConsumer voteListener) {
                Objects.requireNonNull(potText, "potText");
                return new RunItTwiceHandle() {
                    @Override
                    public int currentVote() {
                        return VOTE_NORMAL;
                    }

                    @Override
                    public void updateTally(int normal, int runItTwice) {
                    }

                    @Override
                    public void close() {
                    }
                };
            }

            @Override
            public StraddleHandle showStraddle(int timeoutSeconds,
                    String amountText) {
                CompletableFuture<Integer> decision
                        = CompletableFuture.completedFuture(NO_STRADDLE);
                return new StraddleHandle() {
                    @Override
                    public CompletionStage<Integer> decision() {
                        return decision;
                    }

                    @Override
                    public boolean isOpen() {
                        return false;
                    }

                    @Override
                    public void accept() {
                    }

                    @Override
                    public void decline() {
                    }

                    @Override
                    public void refreshLayout() {
                    }
                };
            }

            @Override
            public RebuyHandle showRebuy(RebuyRequest request) {
                Objects.requireNonNull(request, "request");
                RebuyResult value = new RebuyResult(
                        !request.cancelAllowed(), Math.min(request.maximum(),
                                Math.max(request.minimum(), request.defaultAmount())));
                return new RebuyHandle() {
                    @Override
                    public CompletionStage<RebuyResult> result() {
                        return CompletableFuture.completedFuture(value);
                    }

                    @Override
                    public void close() {
                    }
                };
            }

            @Override
            public CompletionStage<GameOverResult> showGameOver(
                    GameOverRequest request) {
                Objects.requireNonNull(request, "request");
                return CompletableFuture.completedFuture(
                        new GameOverResult(false, 0));
            }

            @Override
            public void replayRecoveredAction(PlayerState.Decision decision,
                    double amount) {
            }

            @Override
            public CloseHandle showRecovery() {
                return () -> { };
            }
        };
    }
}
