/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HARD (the top level) must crush a calling station opponent heads-up. */
class BaselineVsStationTest extends BaselineQualityBase {

    @Test
    @DisplayName("HARD must crush calling station heads-up (>+200 bb/100)")
    void hardCrushesStation() {
        // Floor +200 (was +300 pre-bluff). Post-flop bluffing (Phase 1) costs a
        // little against a *pure* calling station the bot has not yet read — and the
        // harness clears the tracker every session, exaggerating that unread window.
        // The early station read (looksPassiveStation + the active-opponent
        // fold-equity check) cuts those −EV bluffs fast, lifting this from ~+211 to
        // ~+241 bb/100; the residue is the value c-bet line, correctly *kept*
        // (against someone who calls anything a marginal c-bet is thin value, not a
        // bluff). +200 confirms a dominant win without demanding the bot become a
        // readable value-only robot. (Multi-way prints +700+ bb/100 vs 5 stations.)
        double bb100 = runMatchup("STATION-vs-HARD", FixedStrategyBot.Strategy.STATION);
        assertAtLeast("HARD vs STATION", bb100, 200.0);
    }
}
