/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.protocolsim;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.bot.harness.MultiwaySimulator;
import com.tonikelope.coronapoker.core.AnteMode;
import com.tonikelope.coronapoker.bot.harness.TestBotPlayer;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scalable production-bot integrity campaign. This deliberately asserts hard
 * game invariants rather than noisy win-rate thresholds.
 */
@Tag("slow")
@Tag("protocol-sim")
class ProtocolBotCampaignTest {

    private static final int DEFAULT_HANDS = 1000;
    private static final double STARTING_STACK = 200.0;
    private static final double BIG_BLIND = 2.0;
    private static final double EPSILON = 1e-8;

    @BeforeAll
    static void silenceBots() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    void randomizedProductionBotsPreserveHardInvariants() {
        long seed = CampaignSeed.resolve("qa.sim.seed", "production-bot");
        int configuredHands = intProperty("qa.sim.bot.hands", DEFAULT_HANDS, 1, 1_000_000);
        String replayText = System.getProperty("qa.sim.bot.hand");
        int first = replayText == null || replayText.isBlank()
                ? 0 : parseBoundedInt("qa.sim.bot.hand", replayText, 0, configuredHands - 1);
        int end = replayText == null || replayText.isBlank() ? configuredHands : first + 1;
        BotEvaluator evaluator = new MemoizedAlbertaEvaluator();
        int requested = end - first;

        CampaignProgress.report("production-bot", 0, requested);
        for (int hand = first; hand < end; hand++) {
            runOne(seed, hand, evaluator);
            CampaignProgress.report("production-bot", hand - first + 1, requested);
        }
    }

    private static void runOne(long campaignSeed, int hand, BotEvaluator evaluator) {
        long handSeed = mix64(campaignSeed + hand);
        SplittableRandom random = new SplittableRandom(handSeed);
        // Start at the maximum and cover each supported size before randomizing.
        int seats = hand == 0 ? 10 : hand < 9 ? hand + 1 : 2 + random.nextInt(9);
        MultiwaySimulator sim = new MultiwaySimulator(
                seats, handSeed, STARTING_STACK, BIG_BLIND, evaluator);
        AnteMode[] modes = AnteMode.values();
        int ante = random.nextInt(modes.length + 1);
        sim.setForcedBets(ante == modes.length ? null : modes[ante], seats > 2 && random.nextBoolean());
        sim.dealerView().requireLegalBotRaises();
        Bot.Difficulty[] available = Bot.Difficulty.values();
        Bot.Profile[] styles = Bot.Profile.values();
        double startingTotal = 0;
        for (int seat = 0; seat < seats; seat++) {
            Bot.Difficulty difficulty = available[random.nextInt(available.length)];
            Bot.Profile style = hand % 2 == 0 ? null : styles[(hand / 2 + seat) % styles.length];
            sim.bot(seat).configure(difficulty, style);
            double stack = hand == 0 ? STARTING_STACK : random.nextInt(100, 30_001) / 100.0;
            sim.player(seat).setStack(stack);
            startingTotal += stack;
        }

        int buttonBefore = sim.currentButton();
        String context = "seed=" + campaignSeed + " hand=" + hand
                + " handSeed=" + handSeed + " seats=" + seats;
        MultiwaySimulator.HandResult result;
        try {
            result = sim.playOneHand();
        } catch (IllegalStateException failure) {
            throw new IllegalStateException(context + ": " + failure.getMessage(), failure);
        }

        assertEquals((buttonBefore + 1) % seats, sim.currentButton(),
                context + " button did not rotate exactly once");
        assertFalse(result.winners.isEmpty(), context + " has no winner");
        assertTrue(Double.isFinite(result.pot) && result.pot >= 0.0,
                context + " invalid pot=" + result.pot);
        for (int winner : result.winners) {
            assertTrue(winner >= 0 && winner < seats,
                    context + " invalid winner seat=" + winner);
        }

        double total = 0.0;
        for (int seat = 0; seat < seats; seat++) {
            TestBotPlayer player = sim.player(seat);
            double stack = player.getStack();
            double bet = player.getBet();
            assertTrue(Double.isFinite(stack) && stack >= 0.0,
                    context + " seat=" + seat + " invalid stack=" + stack);
            assertTrue(Double.isFinite(bet) && bet >= 0.0,
                    context + " seat=" + seat + " invalid bet=" + bet);
            total += stack;
        }
        assertEquals(startingTotal, total, EPSILON,
                context + " chip conservation failed");
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String text = System.getProperty(name);
        return text == null || text.isBlank()
                ? fallback : parseBoundedInt(name, text, min, max);
    }

    private static int parseBoundedInt(String name, String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new IllegalArgumentException(name + " must be " + min + ".." + max);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer: " + text, ex);
        }
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
