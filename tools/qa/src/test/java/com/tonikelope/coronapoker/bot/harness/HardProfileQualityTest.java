/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
@Tag("bot-campaign")
@Tag("bot-quality")
class HardProfileQualityTest {

    @BeforeAll
    static void quietBots() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @ParameterizedTest
    @EnumSource(Bot.Profile.class)
    void everyHardProfileWinsAgainstFiveTagWithIndependentSeeds(Bot.Profile profile) {
        verify(HardQualityCampaign.Scenario.SIX_TAG, profile, HardQualityCampaign.SESSIONS_PER_SEED);
    }

    @ParameterizedTest
    @EnumSource(value = HardQualityCampaign.Scenario.class, names = {
        "HEADS_UP", "THREE_HANDED", "FOUR_TAG", "FIVE_TAG", "SIX_TAG",
        "SEVEN_TAG", "EIGHT_TAG", "NINE_TAG", "TEN_TAG"
    })
    void naturalHardWinsAtEverySupportedTableSize(HardQualityCampaign.Scenario scenario) {
        int rotation = scenario.seats * 6;
        int sessions = HardQualityCampaign.SESSIONS_PER_SEED / 2 / rotation * rotation;
        verify(scenario, null, sessions);
    }

    @Test
    void stationWinsAgainstTwoTagAndKeepsItsStyle() {
        verify(HardQualityCampaign.Scenario.THREE_HANDED, Bot.Profile.STATION,
                HardQualityCampaign.SESSIONS_PER_SEED);
    }

    @ParameterizedTest
    @EnumSource(Bot.Profile.class)
    void everyHardProfileWinsAtTheMaximumTableSize(Bot.Profile profile) {
        verify(HardQualityCampaign.Scenario.TEN_TAG, profile, 600);
    }

    private void verify(HardQualityCampaign.Scenario scenario, Bot.Profile profile, int defaultSessions) {
        int sessions = QaConfig.sessions(defaultSessions);
        int hands = QaConfig.hands(HardQualityCampaign.HANDS_PER_SESSION);
        String label = profile == null ? scenario.name() : profile.name() + " " + scenario.name();
        boolean threeHanded = scenario == HardQualityCampaign.Scenario.THREE_HANDED;
        long[] seeds = HardQualityCampaign.VALIDATION_SEEDS;
        if (threeHanded) {
            seeds = new long[]{0x1456BC97EA23L, 0xFA51CD208B71L};
        } else if (scenario == HardQualityCampaign.Scenario.TEN_TAG && profile != null) {
            seeds = new long[]{0xD36F901BCA47L, 0x30FE14CD78A9L};
        }
        double[] samples = new double[sessions * seeds.length];
        int offset = 0;
        BotStats aggregate = new BotStats(label);
        var evaluator = new MemoizedAlbertaEvaluator();
        for (long baseSeed : seeds) {
            long seed = QaConfig.seed(baseSeed);
            double[] block = new double[sessions];
            for (int session = 0; session < sessions; session++) {
                BotStats stats = HardQualityCampaign.play(scenario, profile, seed, session, hands, evaluator);
                block[session] = stats.bbPer100(HardQualityCampaign.BIG_BLIND);
                aggregate.add(stats);
            }
            System.arraycopy(block, 0, samples, offset, sessions);
            offset += sessions;
            SessionEstimate estimate = SessionEstimate.of(block);
            report(label + " seed 0x" + Long.toHexString(seed), estimate, sessions * hands);
            assertTrue(estimate.mean() > 0, () -> label + " lost in seed 0x" + Long.toHexString(seed));
        }
        SessionEstimate estimate = SessionEstimate.of(samples);
        report(label + " pooled", estimate, samples.length * hands);
        assertTrue(estimate.clears(0, 1.96), () -> label + " positive edge not established: " + estimate);
        if (profile == Bot.Profile.STATION && scenario != HardQualityCampaign.Scenario.TEN_TAG) {
            assertTrue(aggregate.handsWithPreflopRaise <= (threeHanded ? 0.15 : 0.12) * aggregate.handsPlayed,
                    () -> aggregate.summary(HardQualityCampaign.BIG_BLIND));
            assertTrue(aggregate.postflopBetsRaises < (threeHanded ? 2.35 : 1.65) * aggregate.postflopCalls,
                    () -> aggregate.summary(HardQualityCampaign.BIG_BLIND));
            assertTrue(aggregate.postflopCalls >= 0.42 * (aggregate.postflopCalls + aggregate.postflopFolds),
                    () -> aggregate.summary(HardQualityCampaign.BIG_BLIND));
        }
    }

    private static void report(String label, SessionEstimate estimate, int hands) {
        double halfWidth = 1.96 * estimate.standardError();
        System.out.printf(Locale.ROOT, "%s: %d hands, %+.2f bb/100, 95%% CI [%+.2f, %+.2f]%n",
                label, hands, estimate.mean(), estimate.mean() - halfWidth, estimate.mean() + halfWidth);
    }
}
