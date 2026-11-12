/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
@Tag("bot-campaign")
class MultiwayBaselineVsTagTableTest extends MultiwayBaselineBase {

    @Override
    protected double stackForSession(int session) {
        // Each of the six hero seats plays both depths.
        return session / NUM_SEATS % 2 == 0 ? 40 * BIG_BLIND : STARTING_STACK;
    }

    @Test
    void hardLimitsLossesAgainstASelectiveSixMaxTable() {
        String label = "HARD vs 5 fixed TAG";
        double bb100 = runMatchup(label, FixedStrategyBot.Strategy.TAG);
        assertAtLeast(label, bb100, -30);
    }
}
