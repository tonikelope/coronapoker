/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;

/** Frontend-owned execution boundary for asynchronous and barrier UI work. */
public interface GameUiExecutor {

    void run(Runnable action);

    void runAndWait(Runnable action);

    static GameUiExecutor direct() {
        return new GameUiExecutor() {
            @Override public void run(Runnable action) {
                Objects.requireNonNull(action, "action").run();
            }

            @Override public void runAndWait(Runnable action) {
                Objects.requireNonNull(action, "action").run();
            }
        };
    }
}
