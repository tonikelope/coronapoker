/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Transitional frontend projection for state now owned by {@link GameSession}. */
public interface GameStateMirror {
    void recoveredBuyin(int buyin, boolean rebuy);
    void blindSchedule(int value, int type);
    void recovering(boolean value);

    static GameStateMirror noop() {
        return new GameStateMirror() {
            @Override public void recoveredBuyin(int buyin, boolean rebuy) { }
            @Override public void blindSchedule(int value, int type) { }
            @Override public void recovering(boolean value) { }
        };
    }
}
