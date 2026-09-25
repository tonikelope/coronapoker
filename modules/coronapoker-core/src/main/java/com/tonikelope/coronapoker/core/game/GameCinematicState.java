/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Shared frontend state used to serialize table animation with cinematics. */
public interface GameCinematicState {

    /** Marks a cinematic as active before the dealer can release the next turn. */
    default void start() {
    }

    boolean isPlaying();

    void stop();

    void awaitChange(long timeoutMillis) throws InterruptedException;

    static GameCinematicState idle() {
        return IdleState.INSTANCE;
    }

    /** Thread-safe state for non-Swing frontends such as GDX. */
    static GameCinematicState coordinated() {
        return new CoordinatedState();
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

    final class CoordinatedState implements GameCinematicState {
        private final Object lock = new Object();
        private volatile boolean playing;

        private CoordinatedState() {
        }

        @Override
        public void start() {
            playing = true;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void stop() {
            playing = false;
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public void awaitChange(long timeoutMillis) throws InterruptedException {
            synchronized (lock) {
                if (playing) {
                    lock.wait(timeoutMillis);
                }
            }
        }
    }
}
