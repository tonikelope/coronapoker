/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Localized text lookup required by the canonical game controller. */
@FunctionalInterface
public interface GameText {

    String translate(String key, Object... arguments);

    static GameText keys() {
        return (key, arguments) -> key;
    }
}
