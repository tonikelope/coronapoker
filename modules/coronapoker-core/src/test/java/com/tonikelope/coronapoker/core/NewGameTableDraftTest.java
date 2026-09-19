package com.tonikelope.coronapoker.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;

final class NewGameTableDraftTest {

    @Test
    void defaultDraftMatchesTheExistingNewGameDefaults() {
        NewGameTableDraft draft = new NewGameTableDraft();

        assertEquals(new NewGameTableDraft.BlindLevel(0.1, 0.2), draft.blindLevel());
        assertEquals(10, draft.buyin());
        assertEquals(10, draft.minBuyinBb());
        assertEquals(100, draft.maxBuyinBb());
        assertTrue(draft.fixedBuyin());
        assertTrue(draft.rebuy());
        assertTrue(draft.botRebuy());
        assertTrue(draft.thinkTime());
        assertEquals(40, draft.thinkSeconds());
        assertEquals(10, draft.showdownSeconds());
        assertEquals(NewGameTableDraft.BotDifficulty.MEDIUM, draft.botDifficulty());
    }

    @Test
    void dependentControlsFollowTheirActualParentSwitches() {
        NewGameTableDraft draft = new NewGameTableDraft();

        assertFalse(draft.blindCapControlEnabled());
        draft.setIncreaseBlinds(true);
        draft.setBlindCap(true);
        assertTrue(draft.blindCapRaisesEnabled());
        draft.setRebuyLimit(true);
        assertTrue(draft.rebuyLimitCountEnabled());
        draft.setRebuy(false);
        assertFalse(draft.rebuyLimitCountEnabled());
        assertFalse(draft.botRebuyEnabled());
    }

    @Test
    void recoveredEconomyIsLockedWhileGameRulesRemainEditable() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setEconomyLocked(true);

        assertThrows(IllegalStateException.class, () -> draft.setAnte(true));
        assertThrows(IllegalStateException.class, () -> draft.setFixedBuyin(false));
        assertThrows(IllegalStateException.class, () -> draft.setBlindLevelIndex(1));
        draft.setRebuy(false);
        draft.setIwtsth(true);
        draft.setRunItTwice(true);

        assertFalse(draft.rebuy());
        assertTrue(draft.iwtsth());
        assertTrue(draft.runItTwice());
    }

    @Test
    void valuesClampToTheSwingSpinnerRangesAndSnapshotIsImmutable() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setThinkSeconds(999);
        draft.setShowdownSeconds(0);
        draft.setMaxBuyinBb(400);
        draft.setMinBuyinBb(400);
        draft.setMaxBuyinBb(20);
        draft.setBuyin(Integer.MAX_VALUE);
        NewGameTableDraft.Settings settings = draft.snapshot();

        assertEquals(120, settings.thinkSeconds());
        assertEquals(5, settings.showdownSeconds());
        assertEquals(400, settings.minBuyinBb());
        assertEquals(400, settings.maxBuyinBb());
        assertEquals(draft.maximumBuyin(), settings.buyin());
        assertThrows(UnsupportedOperationException.class,
                () -> settings.blindLevels().clear());
    }

    @Test
    void serializesTheClassicLobbyConfigWithoutFrontendTypes() {
        NewGameTableDraft.Settings settings = new NewGameTableDraft().snapshot();

        assertEquals("10|0.10 / 0.20", settings.gameInfoForWire());
        assertEquals("SB=0.1#BG=0.2#STRUCT=#BUYIN=10#FIXED=1#BMIN=10#BMAX=100"
                + "#REBUY=1#RLIM=0#BOTRB=1#BOTBAL=0#RCAP=0#DBL=0#DTYPE=1"
                + "#BCAP=0.0#MANOS=-1#ANTE=0#STR=0#IWTSTH=0#RIT=0#RABBIT=0"
                + "#THINKT=40#THINKON=1#SHOWDOWN=10#DIFF=MEDIUM",
                settings.serializeForWire());
        assertEquals(settings, NewGameTableDraft.Settings.parseWire(settings.serializeForWire()));
    }

    @Test
    void wireRoundTripPreservesEveryEditableValueAndCustomStructureName() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindStructure("Viernes turbo #1",
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
        draft.setBotDifficulty(NewGameTableDraft.BotDifficulty.HARD);

        NewGameTableDraft.Settings expected = draft.snapshot();

        assertEquals(expected,
                NewGameTableDraft.Settings.parseWire(expected.serializeForWire()));
    }
}
