package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLiveSettingsMergeTest {

    @Test
    void noOpSaveKeepsLatestAuthoritativeConfigurationUntouched() {
        GameConfigCodecV1.Configuration opened = configuration();
        GameConfigCodecV1.Configuration current = opened.withBlindSettings(
                0.20d, 0.40d, 15, 2, 3.20d,
                List.of(new GameConfigCodecV1.BlindLevel(0.20d, 0.40d)));

        assertSame(current, GdxLiveSettingsMerge.merge(opened, opened,
                current));
    }

    @Test
    void changedRuleIsAppliedWithoutRollingBackConcurrentBlindAdvance() {
        GameConfigCodecV1.Configuration opened = configuration();
        GameConfigCodecV1.Configuration edited = opened.withIwtsth(true)
                .withHands(7);
        GameConfigCodecV1.Configuration current = opened.withBlindSettings(
                0.20d, 0.40d, 15, 2, 3.20d,
                List.of(new GameConfigCodecV1.BlindLevel(0.20d, 0.40d)));

        GameConfigCodecV1.Configuration merged = GdxLiveSettingsMerge.merge(
                opened, edited, current);

        assertEquals(7, merged.hands());
        assertEquals(true, merged.iwtsth());
        assertEquals(0.20d, merged.smallBlind());
        assertEquals(0.40d, merged.bigBlind());
        assertEquals(15, merged.blindsDouble());
        assertEquals(2, merged.blindsDoubleType());
        assertEquals(3.20d, merged.blindCap());
        assertEquals(current.blindStructure(), merged.blindStructure());
    }

    @Test
    void changedBlindSettingsDoNotRollBackConcurrentRuleUpdate() {
        GameConfigCodecV1.Configuration opened = configuration();
        GameConfigCodecV1.Configuration edited = opened.withBlindSettings(
                0.25d, 0.50d, 20, 1, 4d,
                List.of(new GameConfigCodecV1.BlindLevel(0.25d, 0.50d)));
        GameConfigCodecV1.Configuration current = opened.withRunItTwice(true)
                .withRabbitHunting(2).withBotRebuy(false);

        GameConfigCodecV1.Configuration merged = GdxLiveSettingsMerge.merge(
                opened, edited, current);

        assertEquals(true, merged.runItTwice());
        assertEquals(2, merged.rabbitHunting());
        assertEquals(false, merged.botRebuy());
        assertEquals(0.25d, merged.smallBlind());
        assertEquals(0.50d, merged.bigBlind());
        assertEquals(20, merged.blindsDouble());
        assertEquals(1, merged.blindsDoubleType());
        assertEquals(4d, merged.blindCap());
        assertEquals(edited.blindStructure(), merged.blindStructure());
    }

    @Test
    void liveSaveCannotRewriteSettingsFixedWhenTheTableWasCreated() {
        GameConfigCodecV1.Configuration opened = configuration();
        GameConfigCodecV1.Configuration edited =
                new GameConfigCodecV1.Configuration(
                        999, opened.smallBlind(), opened.bigBlind(),
                        opened.blindsDouble(), opened.blindsDoubleType(),
                        true, "tampered-session", false, opened.hands(),
                        opened.blindCap(), 99, opened.botRebuy(), false,
                        250, 300, 1, opened.ante(), opened.straddle(),
                        true, opened.runItTwice(), opened.rabbitHunting(),
                        120, false, 120, opened.botBalanceToHumans(),
                        opened.blindStructure());

        GameConfigCodecV1.Configuration merged = GdxLiveSettingsMerge.merge(
                opened, edited, opened);

        // IWTSTH is live-editable and therefore does cross the boundary.
        assertEquals(true, merged.iwtsth());
        // Creation-only/session fields must always retain the authoritative
        // table values, even if a malformed UI draft attempted to change them.
        assertEquals(opened.buyin(), merged.buyin());
        assertEquals(opened.recover(), merged.recover());
        assertEquals(opened.sessionId(), merged.sessionId());
        assertEquals(opened.rebuy(), merged.rebuy());
        assertEquals(opened.rebuyLimit(), merged.rebuyLimit());
        assertEquals(opened.fixedBuyin(), merged.fixedBuyin());
        assertEquals(opened.buyinMinBb(), merged.buyinMinBb());
        assertEquals(opened.buyinMaxBb(), merged.buyinMaxBb());
        assertEquals(opened.rebuyCapPolicy(), merged.rebuyCapPolicy());
        assertEquals(opened.thinkTime(), merged.thinkTime());
        assertEquals(opened.thinkTimeEnabled(), merged.thinkTimeEnabled());
        assertEquals(opened.showdownTime(), merged.showdownTime());
    }

    private static GameConfigCodecV1.Configuration configuration() {
        return new GameConfigCodecV1.Configuration(
                10, 0.10d, 0.20d, 0, 1, false, "merge-test",
                true, 100, 0d, 3, true, true, 10, 100,
                0, false, false, false, false, 0, 40,
                true, 10, false, List.of());
    }
}
