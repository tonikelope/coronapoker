/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

/** Frontend window controls that are not poker rules or transport state. */
public interface GameWindowSink {

    boolean isOpen();

    void requestExit();

    void setExitEnabled(boolean enabled);

    void setFullscreenEnabled(boolean enabled);

    void resetImmediateRebuy();

    void setGameOverDialogOpen(boolean open);

    void applyAutomaticFullscreen(boolean enabled);

    void stopGameClock();

    void finishTransmission(boolean transmissionEnded);

    static GameWindowSink noop() {
        return new GameWindowSink() {
            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public void requestExit() {
            }

            @Override
            public void setExitEnabled(boolean enabled) {
            }

            @Override
            public void setFullscreenEnabled(boolean enabled) {
            }

            @Override
            public void resetImmediateRebuy() {
            }

            @Override
            public void setGameOverDialogOpen(boolean open) {
            }

            @Override
            public void applyAutomaticFullscreen(boolean enabled) {
            }

            @Override
            public void stopGameClock() {
            }

            @Override
            public void finishTransmission(boolean transmissionEnded) {
            }
        };
    }
}
