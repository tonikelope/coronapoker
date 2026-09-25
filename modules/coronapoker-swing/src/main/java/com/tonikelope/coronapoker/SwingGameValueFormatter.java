/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameValueFormatter;

/** Exact adapter for the established Swing text representation. */
final class SwingGameValueFormatter implements GameValueFormatter {

    @Override
    public String money(double amount) {
        return Helpers.money2String(amount);
    }

    @Override
    public String elapsed(long seconds) {
        return Helpers.seconds2FullTime(seconds);
    }

    @Override
    public String framedTitle(String text) {
        return Helpers.framedTitle(text);
    }

    @Override
    public float decimal(float value) {
        return Helpers.floatClean(value);
    }
}
