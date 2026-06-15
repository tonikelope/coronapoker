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
 * Aggregated per-bot statistics over a benchmark run. The simulator increments
 * these counters as each decision is observed; tests read the derived ratios
 * (VPIP, PFR, AF, WTSD, bb/100) to assess play quality against industry norms.
 *
 * Counter semantics follow the standard poker tracker conventions:
 *
 * <ul>
 *   <li><b>VPIP</b> = hands where the bot voluntarily put money in the pot
 *       preflop (calls or raises, not blinds posted).</li>
 *   <li><b>PFR</b>  = hands with at least one preflop raise.</li>
 *   <li><b>AF</b>   = postflop aggression factor =
 *       (bets + raises) / calls.</li>
 *   <li><b>WTSD</b> = % of hands that reached showdown.</li>
 *   <li><b>W$SD</b> = % of showdowns won.</li>
 *   <li><b>bb/100</b> = winrate in big blinds per 100 hands.</li>
 * </ul>
 */
public final class BotStats {

    public final String label;

    // Hands -------------------------------------------------------
    public int handsPlayed;
    public int handsVoluntaryMoneyPreflop;   // VPIP numerator
    public int handsWithPreflopRaise;        // PFR numerator
    public int handsReachedShowdown;         // WTSD numerator
    public int handsWonAtShowdown;           // W$SD numerator
    public int handsWon;                     // total hands won (showdown + opponent fold)

    // Postflop actions for AF -------------------------------------
    public int postflopBetsRaises;
    public int postflopCalls;
    public int postflopFolds;
    public int postflopChecks;

    // Bet composition by made-hand strength at the moment of the aggressive
    // action — how readable / exploitable the bot is. A bot that only ever
    // fires with strong hands (bluff% ~ 0) is a transparent value-only player
    // a human can fold against on sight; a human-like bot mixes in credible
    // bluffs. Strength is raw equity vs one random hand (see the simulators).
    public static final double VALUE_BET_STRENGTH = 0.66;
    public static final double BLUFF_BET_STRENGTH = 0.45;
    public int postflopValueBets;            // strength >= VALUE_BET_STRENGTH
    public int postflopBluffBets;            // strength <= BLUFF_BET_STRENGTH
    public int riverBets;                    // aggressive actions taken on the river
    public int riverBluffBets;               // of those, with strength <= BLUFF_BET_STRENGTH

    // C-bet (preflop aggressor on flop) ---------------------------
    public int cbetOpportunities;            // flop seen with preflop initiative + first to act or checked-to
    public int cbetExecuted;

    // Chips -------------------------------------------------------
    public float netChipsWon;                // final stack - starting stack accumulated

    public BotStats(String label) {
        this.label = label;
    }

    /** Accumulate counters from another BotStats into this one. */
    public void add(BotStats other) {
        handsPlayed += other.handsPlayed;
        handsVoluntaryMoneyPreflop += other.handsVoluntaryMoneyPreflop;
        handsWithPreflopRaise += other.handsWithPreflopRaise;
        handsReachedShowdown += other.handsReachedShowdown;
        handsWonAtShowdown += other.handsWonAtShowdown;
        handsWon += other.handsWon;
        postflopBetsRaises += other.postflopBetsRaises;
        postflopCalls += other.postflopCalls;
        postflopFolds += other.postflopFolds;
        postflopChecks += other.postflopChecks;
        postflopValueBets += other.postflopValueBets;
        postflopBluffBets += other.postflopBluffBets;
        riverBets += other.riverBets;
        riverBluffBets += other.riverBluffBets;
        cbetOpportunities += other.cbetOpportunities;
        cbetExecuted += other.cbetExecuted;
        netChipsWon += other.netChipsWon;
    }

    public double vpip() {
        return handsPlayed == 0 ? 0 : 100.0 * handsVoluntaryMoneyPreflop / handsPlayed;
    }

    public double pfr() {
        return handsPlayed == 0 ? 0 : 100.0 * handsWithPreflopRaise / handsPlayed;
    }

    public double af() {
        if (postflopCalls == 0) {
            return postflopBetsRaises == 0 ? 0.0 : 99.0;
        }
        return 1.0 * postflopBetsRaises / postflopCalls;
    }

    public double wtsdPct() {
        return handsPlayed == 0 ? 0 : 100.0 * handsReachedShowdown / handsPlayed;
    }

    public double wsdPct() {
        return handsReachedShowdown == 0 ? 0 : 100.0 * handsWonAtShowdown / handsReachedShowdown;
    }

    public double winRatePct() {
        return handsPlayed == 0 ? 0 : 100.0 * handsWon / handsPlayed;
    }

    public double cbetPct() {
        return cbetOpportunities == 0 ? 0 : 100.0 * cbetExecuted / cbetOpportunities;
    }

    /** Share of postflop bets/raises made with a weak hand (bluffs + air semibluffs). */
    public double bluffBetPct() {
        return postflopBetsRaises == 0 ? 0 : 100.0 * postflopBluffBets / postflopBetsRaises;
    }

    /** Share of postflop bets/raises made with a strong made hand (value). */
    public double valueBetPct() {
        return postflopBetsRaises == 0 ? 0 : 100.0 * postflopValueBets / postflopBetsRaises;
    }

    /** Share of river bets that are bluffs — the most telling readability metric. */
    public double riverBluffPct() {
        return riverBets == 0 ? 0 : 100.0 * riverBluffBets / riverBets;
    }

    public double bbPer100(float bb) {
        return handsPlayed == 0 ? 0 : 100.0 * netChipsWon / (handsPlayed * bb);
    }

    /** Format as a single-line summary for benchmark logs. */
    public String summary(float bb) {
        return String.format(
                "%-22s n=%4d  VPIP=%5.1f%%  PFR=%5.1f%%  AF=%4.2f  WTSD=%5.1f%%  W$SD=%5.1f%%  cbet=%5.1f%%  bluff=%4.1f%%  rvBluff=%4.1f%%  Win=%5.1f%%  bb/100=%+7.1f",
                label, handsPlayed, vpip(), pfr(), af(), wtsdPct(), wsdPct(), cbetPct(),
                bluffBetPct(), riverBluffPct(), winRatePct(), bbPer100(bb));
    }
}
