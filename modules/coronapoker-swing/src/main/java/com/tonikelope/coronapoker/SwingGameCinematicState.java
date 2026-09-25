/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameCinematicState;

/** Preserves the classic cross-seat cinematic coordination contract. */
final class SwingGameCinematicState implements GameCinematicState {

    @Override
    public void start() {
        Init.PLAYING_CINEMATIC = true;
    }

    @Override
    public boolean isPlaying() {
        return Init.PLAYING_CINEMATIC;
    }

    @Override
    public void stop() {
        Init.PLAYING_CINEMATIC = false;
        synchronized (Init.LOCK_CINEMATICS) {
            Init.LOCK_CINEMATICS.notifyAll();
        }
    }

    @Override
    public void awaitChange(long timeoutMillis) throws InterruptedException {
        synchronized (Init.LOCK_CINEMATICS) {
            if (Init.PLAYING_CINEMATIC) {
                Init.LOCK_CINEMATICS.wait(timeoutMillis);
            }
        }
    }
}
