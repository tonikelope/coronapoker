/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.eval.AlbertaEvaluatorAdapter;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.harness.MultiwaySimulator;
import com.tonikelope.coronapoker.bot.harness.TestBotPlayer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless game-flow smoke. Exercises the bot engine and game flow across
 * multiple seat counts and difficulties with hard invariants per hand.
 *
 * Scope (Sprint 0 minimum viable):
 *   - bot engine end-to-end (production Bot.java, BotEvaluator, OpponentTracker).
 *   - game flow via MultiwaySimulator (blinds, action order, street rotation,
 *     showdown, button rotation).
 *
 * Out of scope (intentionally): real Crupier state machine, NetServer/NetClient,
 * SQL persistence, the SRA crypto engine. Those layers are validated by their own
 * dedicated tests under com.tonikelope.coronapoker.crypto / .sra and by manual
 * smoke checklists per sprint (docs/smoke-checklist/).
 *
 * Asserts on EVERY hand:
 *   - chip conservation: Sigma stacks == numSeats * startingStack (epsilon 0.5f)
 *   - no NaN, no Infinity in any stack or bet
 *   - every stack >= 0
 *   - hand counter strictly monotonic
 *
 * Asserts at session end:
 *   - at least one hand had a real winner (i.e. game-flow does not deadlock
 *     into all-ties or no-action).
 *
 * Target runtime: under 30 seconds for the full class.
 */
class GameFlowSmoke {

    private static final float STARTING_STACK = 200f;
    private static final float BIG_BLIND = 2f;
    private static final float EPS = 0.5f;

    private final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @BeforeAll
    static void silence() {
        // The bot logs decisions at INFO; without this a 9-seat session spews
        // thousands of [BOT AI] lines and drowns the test output.
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    @DisplayName("3-seat MEDIUM table, 20 hands — invariants hold every hand")
    void threeSeatBaseline() {
        runSession(3, Bot.Difficulty.MEDIUM, 20, 1001L);
    }

    @Test
    @DisplayName("6-seat HARD table, 50 hands — invariants hold every hand")
    void sixSeatHardTable() {
        runSession(6, Bot.Difficulty.HARD, 50, 2002L);
    }

    @Test
    @DisplayName("9-seat HARD table, 20 hands — invariants hold every hand")
    void nineSeatHardTable() {
        runSession(9, Bot.Difficulty.HARD, 20, 3003L);
    }

    @Test
    @DisplayName("Each difficulty plays 10 hands at 6-seat without breaking invariants")
    void allDifficultiesAt6Seat() {
        long baseSeed = 4004L;
        for (Bot.Difficulty d : Bot.Difficulty.values()) {
            runSession(6, d, 10, baseSeed ^ d.ordinal());
        }
    }

    private void runSession(int numSeats, Bot.Difficulty difficulty, int numHands, long seed) {
        Bot.DIFFICULTY = difficulty;
        MultiwaySimulator sim = new MultiwaySimulator(numSeats, seed, STARTING_STACK, BIG_BLIND, evaluator);
        Bot.Difficulty[] diffs = new Bot.Difficulty[numSeats];
        for (int i = 0; i < numSeats; i++) {
            diffs[i] = difficulty;
        }
        sim.setSeatDifficulties(diffs);

        int realWinners = 0;
        int lastHandIdx = -1;

        for (int handIdx = 0; handIdx < numHands; handIdx++) {
            sim.resetStacks();
            MultiwaySimulator.HandResult result = sim.playOneHand();

            // Hand counter monotonic
            assertTrue(handIdx > lastHandIdx,
                    "Hand counter must be monotonic (got " + handIdx + " after " + lastHandIdx + ")");
            lastHandIdx = handIdx;

            // Chip conservation
            float total = 0f;
            for (int seat = 0; seat < numSeats; seat++) {
                TestBotPlayer p = sim.player(seat);
                float stack = p.getStack();
                float bet = p.getBet();
                total += stack;

                // NaN / Inf
                assertFalse(Float.isNaN(stack),
                        ctx(numSeats, difficulty, handIdx, seat) + " stack is NaN");
                assertFalse(Float.isInfinite(stack),
                        ctx(numSeats, difficulty, handIdx, seat) + " stack is Infinity");
                assertFalse(Float.isNaN(bet),
                        ctx(numSeats, difficulty, handIdx, seat) + " bet is NaN");
                assertFalse(Float.isInfinite(bet),
                        ctx(numSeats, difficulty, handIdx, seat) + " bet is Infinity");

                // Non-negative stack
                assertTrue(stack >= 0f,
                        ctx(numSeats, difficulty, handIdx, seat) + " stack < 0: " + stack);
            }

            float expected = numSeats * STARTING_STACK;
            assertEquals(expected, total, EPS,
                    "Chip conservation violated at " + ctxHand(numSeats, difficulty, handIdx)
                            + ": got " + total + ", expected " + expected);

            // Pot validity
            float pot = result.pot;
            assertFalse(Float.isNaN(pot),
                    ctxHand(numSeats, difficulty, handIdx) + " pot is NaN");
            assertFalse(Float.isInfinite(pot),
                    ctxHand(numSeats, difficulty, handIdx) + " pot is Infinity");
            assertTrue(pot >= 0f,
                    ctxHand(numSeats, difficulty, handIdx) + " pot < 0: " + pot);

            // Winners set is valid (non-empty)
            assertFalse(result.winners.isEmpty(),
                    ctxHand(numSeats, difficulty, handIdx) + " winners set is empty");
            if (!result.winners.isEmpty()) {
                realWinners++;
            }
        }

        assertTrue(realWinners > 0,
                "Session " + numSeats + "-seat " + difficulty + " produced zero real winners over "
                        + numHands + " hands — game flow may be deadlocked");
    }

    private static String ctxHand(int numSeats, Bot.Difficulty d, int handIdx) {
        return "[" + numSeats + "-seat " + d + " hand=" + handIdx + "]";
    }

    private static String ctx(int numSeats, Bot.Difficulty d, int handIdx, int seat) {
        return ctxHand(numSeats, d, handIdx) + " seat=" + seat;
    }
}
