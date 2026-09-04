/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Shared frontend state used to serialize table animation with cinematics. */
public interface GameCinematicState {

    boolean isPlaying();

    void stop();

    void awaitChange(long timeoutMillis) throws InterruptedException;

    static GameCinematicState idle() {
        return IdleState.INSTANCE;
    }

    final class IdleState implements GameCinematicState {
        private static final IdleState INSTANCE = new IdleState();

        private IdleState() {
        }

        @Override
        public boolean isPlaying() {
            return false;
        }

        @Override
        public void stop() {
        }

        @Override
        public void awaitChange(long timeoutMillis) {
        }
    }
}
