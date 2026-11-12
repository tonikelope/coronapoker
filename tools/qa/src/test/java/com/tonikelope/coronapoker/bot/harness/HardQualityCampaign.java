/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.bot.harness.FixedStrategyBot.Strategy;
import com.tonikelope.coronapoker.core.AnteMode;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Seeded scenarios for the extended bot quality campaign. */
final class HardQualityCampaign {

    static final long[] VALIDATION_SEEDS = {0x6B5CE3A18417L, 0x8F279D4BE06AL};
    static final int SESSIONS_PER_SEED = 1008;
    static final int HANDS_PER_SESSION = 60;
    static final float BIG_BLIND = 2;
    private static final int[] STANDARD_DEPTHS = {15, 25, 40, 65, 100, 160};
    private static final int[] FORCED_DEPTHS = {1, 3, 6, 9, 15, 40, 100, 250};
    private static final Strategy[] MIXED_FIELD = {Strategy.TAG, Strategy.ROCK, Strategy.TAG,
        Strategy.STATION, Strategy.TAG, Strategy.MANIAC, Strategy.TAG, Strategy.ROCK, Strategy.STATION};
    private static final Bot.Profile[] PROFILES = Bot.Profile.values();
    private static final AnteMode[] ANTE_MODES = {null, AnteMode.TRADITIONAL, AnteMode.BIG_BLIND,
        AnteMode.BUTTON, AnteMode.SMALL_BLIND};

    enum Scenario {
        HEADS_UP(2, false, false),
        THREE_HANDED(3, false, false),
        FOUR_TAG(4, false, false),
        FIVE_TAG(5, false, false),
        SIX_TAG(6, false, false),
        SEVEN_TAG(7, false, false),
        EIGHT_TAG(8, false, false),
        NINE_TAG(9, false, false),
        TEN_MIXED(10, true, false),
        TEN_TAG(10, false, false),
        ADAPTIVE_FIELD(6, false, false),
        FORCED_BETS(6, false, true);

        final int seats;
        final boolean mixed;
        final boolean forced;

        Scenario(int seats, boolean mixed, boolean forced) {
            this.seats = seats;
            this.mixed = mixed;
            this.forced = forced;
        }
    }

    private HardQualityCampaign() {}

    static BotStats play(Scenario scenario, Bot.Profile profile, long seed, int session, int hands,
            MemoizedAlbertaEvaluator evaluator) {
        int hero = session % scenario.seats;
        long sessionSeed = seed + session * 0x9E3779B97F4A7C15L;
        if (scenario == Scenario.HEADS_UP) {
            var sim = new HeadsUpSimulator(sessionSeed, depth(scenario, session, 0) * BIG_BLIND,
                    BIG_BLIND, evaluator);
            TestBotPlayer heroPlayer = hero == 0 ? sim.playerA() : sim.playerB();
            TestBotPlayer rival = hero == 0 ? sim.playerB() : sim.playerA();
            Bot heroBot = hero(heroPlayer, profile, sessionSeed);
            Bot tag = new FixedStrategyBot(rival, Strategy.TAG, sessionSeed ^ 0x1EE7D00DL);
            sim.setBotA(hero == 0 ? heroBot : tag);
            sim.setBotB(hero == 0 ? tag : heroBot);
            for (int hand = 0; hand < hands; hand++) {
                sim.resetStacks();
                sim.playOneHand(hand % 2 == 0);
            }
            return hero == 0 ? sim.statsA() : sim.statsB();
        }

        var sim = new MultiwaySimulator(scenario.seats, sessionSeed, 200, BIG_BLIND, evaluator);
        if (scenario.forced) {
            int rules = session / (scenario.seats * FORCED_DEPTHS.length);
            sim.setForcedBets(ANTE_MODES[(rules / 2) % ANTE_MODES.length], rules % 2 != 0);
        }
        for (int seat = 0, villain = 0; seat < scenario.seats; seat++) {
            if (seat == hero) {
                sim.setBot(seat, hero(sim.player(seat), profile, sessionSeed));
            } else if (scenario == Scenario.ADAPTIVE_FIELD) {
                Bot.Profile style = PROFILES[(villain++ + session / scenario.seats) % PROFILES.length];
                sim.setBot(seat, hero(sim.player(seat), style, sessionSeed ^ (0x1EE7D00DL + seat)));
            } else {
                Strategy strategy = scenario.mixed
                        ? MIXED_FIELD[(villain++ + session / scenario.seats) % MIXED_FIELD.length]
                        : Strategy.TAG;
                sim.setBot(seat, new FixedStrategyBot(sim.player(seat), strategy, sessionSeed ^ (0x1EE7D00DL + seat)));
            }
        }
        for (int hand = 0; hand < hands; hand++) {
            double total = 0;
            for (int seat = 0; seat < scenario.seats; seat++) {
                int clockwise = (seat - hero + scenario.seats) % scenario.seats;
                int stackOffset = scenario == Scenario.ADAPTIVE_FIELD ? hand / 30 : 0;
                double stack = depth(scenario, session, clockwise + stackOffset) * BIG_BLIND;
                total += stack;
                sim.player(seat).setStack(stack);
            }
            sim.playOneHand();
            for (int seat = 0; seat < scenario.seats; seat++) {
                double stack = sim.player(seat).getStack();
                if (!Double.isFinite(stack) || stack < 0) {
                    throw new IllegalStateException("Invalid settled stack in session " + session + " hand " + hand);
                }
                total -= stack;
            }
            if (Math.abs(total) > 0.001) {
                throw new IllegalStateException("Unbalanced settlement in session " + session + " hand " + hand);
            }
        }
        return sim.stats(hero);
    }

    private static Bot hero(TestBotPlayer player, Bot.Profile profile, long seed) {
        var bot = new Bot(player, new Random(seed ^ 0xC03FFEE1L));
        bot.configure(Bot.Difficulty.HARD, profile);
        return bot;
    }

    private static int depth(Scenario scenario, int session, int clockwise) {
        int[] depths = scenario.forced ? FORCED_DEPTHS : STANDARD_DEPTHS;
        return depths[(clockwise + session / scenario.seats) % depths.length];
    }

    // Arguments: scenario, profile (or NATURAL), seed, first session, count, hands, CSV.
    public static void main(String[] args) throws Exception {
        Scenario scenario = Scenario.valueOf(args[0]);
        Bot.Profile profile = args[1].equals("NATURAL") ? null : Bot.Profile.valueOf(args[1]);
        long seed = Long.decode(args[2]);
        int first = Integer.parseInt(args[3]);
        int sessions = Integer.parseInt(args[4]);
        int hands = Integer.parseInt(args[5]);
        var evaluator = new MemoizedAlbertaEvaluator();
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
        try (var output = new PrintWriter(Files.newBufferedWriter(Path.of(args[6]), StandardCharsets.UTF_8))) {
            output.println("session,heroSeat,heroDepth,hands,bbPer100,vpip,pfr,betsRaises,calls,folds");
            for (int session = first; session < first + sessions; session++) {
                BotStats stats = play(scenario, profile, seed, session, hands, evaluator);
                output.printf(Locale.ROOT, "%d,%d,%d,%d,%.6f,%d,%d,%d,%d,%d%n", session,
                        session % scenario.seats, depth(scenario, session, 0), stats.handsPlayed,
                        stats.bbPer100(BIG_BLIND), stats.handsVoluntaryMoneyPreflop, stats.handsWithPreflopRaise,
                        stats.postflopBetsRaises, stats.postflopCalls, stats.postflopFolds);
                output.flush();
            }
            if (output.checkError()) {
                throw new IllegalStateException("Could not write the campaign results");
            }
        }
    }
}
