/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

/**
 * Runtime knobs for the matchup/baseline benchmarks so volume can be lowered for
 * fast local iteration without editing source.
 *
 * <ul>
 *   <li><b>Validation mode (default)</b>: {@code mvn -o test} — uses each test's
 *       full volume (200 sessions × 50 hands = 10 000 hands/matchup).</li>
 *   <li><b>Iteration mode</b>: {@code mvn -o test -Dqa.sessions=40 -Dqa.hands=25}
 *       — ~1 000 hands/matchup, minutes instead of hours, for tuning a leak
 *       before committing to the full validation sweep.</li>
 * </ul>
 *
 * <p>The qa {@code pom.xml} forwards these properties into the surefire forks via
 * {@code <systemPropertyVariables>}; when a property is not supplied it arrives as
 * the unresolved literal {@code "${qa.sessions}"}, which {@link Integer#getInteger}
 * cannot parse and therefore falls back to the per-test default passed here.</p>
 */
final class QaConfig {

    private QaConfig() {
    }

    /** Sessions per matchup, overridable with {@code -Dqa.sessions=N}. */
    static int sessions(int defaultValue) {
        Integer v = Integer.getInteger("qa.sessions");
        return (v != null && v > 0) ? v : defaultValue;
    }

    /** Hands per session, overridable with {@code -Dqa.hands=N}. */
    static int hands(int defaultValue) {
        Integer v = Integer.getInteger("qa.hands");
        return (v != null && v > 0) ? v : defaultValue;
    }
}
