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
        };
    }
}
