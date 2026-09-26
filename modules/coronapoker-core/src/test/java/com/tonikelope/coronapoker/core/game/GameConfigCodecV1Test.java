package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConfigCodecV1Test {

    @Test
    void settingsRoundTripKeepsCanonicalBytes() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setIncreaseBlinds(true);
        draft.setBlindCap(true);
        draft.setRunItTwice(true);
        GameConfigCodecV1.Configuration original = GameConfigCodecV1.fromSettings(
                draft.snapshot(), false, "session-123");

        byte[] encoded = GameConfigCodecV1.encode(original);
        GameConfigCodecV1.Result decoded = GameConfigCodecV1.decode(encoded);

        assertTrue(decoded.isOk(), decoded.error());
        assertEquals(original, decoded.value());
        assertArrayEquals(encoded, GameConfigCodecV1.encode(decoded.value()));
    }

    @Test
    void strictPacketRejectsTrailingDataAndInvalidSettings() {
        GameConfigCodecV1.Configuration valid = GameConfigCodecV1.fromSettings(
                new NewGameTableDraft().snapshot(), false, "session-123");
        byte[] encoded = GameConfigCodecV1.encode(valid);

        assertFalse(GameConfigCodecV1.decode(Arrays.copyOf(encoded,
                encoded.length + 1)).isOk());
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.fromSettings(new NewGameTableDraft().snapshot(),
                        false, " "));
    }

    @Test
    void canonicalHashIsStableAndOlderWireVersionsAreRejected() {
        GameConfigCodecV1.Configuration valid = defaultConfiguration();
        assertArrayEquals(GameConfigCodecV1.canonicalHash(valid),
                GameConfigCodecV1.canonicalHash(defaultConfiguration()));
        byte[] encoded = GameConfigCodecV1.encode(valid);
        encoded[7] = 0;
        assertFalse(GameConfigCodecV1.decode(encoded).isOk());
    }

    @Test
    void rejectsNonFiniteMoneyAndCrossFieldRangesWithoutPublishingThem() {
        GameConfigCodecV1.Configuration valid = defaultConfiguration();
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid, Double.NaN,
                        valid.bigBlind(), valid.buyinMinBb(),
                        valid.buyinMaxBb(), valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid,
                        Double.POSITIVE_INFINITY, valid.bigBlind(),
                        valid.buyinMinBb(), valid.buyinMaxBb(),
                        valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid,
                        valid.smallBlind(), Double.NEGATIVE_INFINITY,
                        valid.buyinMinBb(), valid.buyinMaxBb(),
                        valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid, Double.MAX_VALUE,
                        valid.bigBlind(), valid.buyinMinBb(),
                        valid.buyinMaxBb(), valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid,
                        valid.smallBlind(), 1e100, valid.buyinMinBb(),
                        valid.buyinMaxBb(), valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid, 2d, 1d,
                        valid.buyinMinBb(), valid.buyinMaxBb(),
                        valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid,
                        valid.smallBlind(), valid.bigBlind(), 200, 100,
                        valid.rebuyCapPolicy())));
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.requireValid(copy(valid,
                        valid.smallBlind(), valid.bigBlind(),
                        valid.buyinMinBb(), valid.buyinMaxBb(), 99)));
        assertEquals(valid, GameConfigCodecV1.requireValid(valid));
    }

    @Test
    void everyNewGameRuleReachesTheAuthoritativeLaunchConfiguration() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindStructure("Turbo",
                List.of(new NewGameTableDraft.BlindLevel(0.25, 0.5),
                        new NewGameTableDraft.BlindLevel(0.5, 1),
                        new NewGameTableDraft.BlindLevel(1, 2)), 1);
        draft.setIncreaseBlinds(true);
        draft.setBlindIncreaseType(NewGameTableDraft.BlindIncreaseType.HANDS);
        draft.setBlindInterval(17);
        draft.setBlindCap(true);
        draft.setBlindCapRaises(1);
        draft.setFixedBuyin(false);
        draft.setMinBuyinBb(25);
        draft.setMaxBuyinBb(150);
        draft.setBuyin(75);
        draft.setRebuy(true);
        draft.setRebuyLimit(true);
        draft.setRebuyLimitCount(7);
        draft.setBotRebuy(false);
        draft.setBotBalanceToHumans(true);
        draft.setRebuyCapPolicy(NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK);
        draft.setHandLimit(true);
        draft.setHandLimitCount(73);
        draft.setThinkTime(false);
        draft.setThinkSeconds(65);
        draft.setShowdownSeconds(25);
        draft.setAnte(true);
        draft.setStraddle(true);
        draft.setIwtsth(true);
        draft.setRunItTwice(true);
        draft.setRabbitHunting(
                NewGameTableDraft.RabbitHunting.FREE_SMALL_AND_BIG_BLIND);

        GameConfigCodecV1.Configuration actual = GameConfigCodecV1.fromSettings(
                draft.snapshot(), true, "session-all-settings");

        assertEquals(new GameConfigCodecV1.Configuration(75, 0.5, 1,
                17, 2, true, "session-all-settings", true, 73, 2, 7,
                false, false, 25, 150, 1, true, true, true, true, 3,
                65, false, 25, true,
                List.of(new GameConfigCodecV1.BlindLevel(0.25, 0.5),
                        new GameConfigCodecV1.BlindLevel(0.5, 1),
                        new GameConfigCodecV1.BlindLevel(1, 2))), actual);
    }

    private static GameConfigCodecV1.Configuration defaultConfiguration() {
        return GameConfigCodecV1.fromSettings(
                new NewGameTableDraft().snapshot(), false, "session-default");
    }

    private static GameConfigCodecV1.Configuration copy(
            GameConfigCodecV1.Configuration value, double smallBlind,
            double bigBlind, int minBuyin, int maxBuyin, int rebuyCapPolicy) {
        return new GameConfigCodecV1.Configuration(value.buyin(), smallBlind,
                bigBlind,
                value.blindsDouble(), value.blindsDoubleType(), value.recover(),
                value.sessionId(), value.rebuy(), value.hands(),
                value.blindCap(), value.rebuyLimit(), value.botRebuy(),
                value.fixedBuyin(), minBuyin, maxBuyin, rebuyCapPolicy,
                value.ante(), value.straddle(), value.iwtsth(),
                value.runItTwice(), value.rabbitHunting(), value.thinkTime(),
                value.thinkTimeEnabled(), value.showdownTime(),
                value.botBalanceToHumans(), value.blindStructure());
    }
}
