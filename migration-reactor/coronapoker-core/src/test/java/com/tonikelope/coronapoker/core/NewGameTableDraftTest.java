package com.tonikelope.coronapoker.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
