/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

/** UI-neutral outlet for the table's shared progress/countdown indicator. */
public interface GameProgressSink {

    void countdown(int seconds);

    void indeterminate();

    void reset(int seconds);

    void setIndeterminate(boolean enabled);

    static GameProgressSink noop() {
        return new GameProgressSink() {
            @Override
            public void countdown(int seconds) {
            }

            @Override
            public void indeterminate() {
            }

            @Override
            public void reset(int seconds) {
            }

            @Override
            public void setIndeterminate(boolean enabled) {
            }
        };
    }
}
