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
import com.tonikelope.coronapoker.GameFrame;
import com.tonikelope.coronapoker.GamePreset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@link GamePreset} contract: a preset must round-trip EVERY new-game
 * setting (including the chosen blind structure), the registry must persist
 * renames/deletions, and corrupt entries must be skipped rather than fatal.
 *
 * <p>Pure logic — exercises the serializer/registry against in-memory state and a
 * local {@link Properties}, so it never reads or writes the user's real
 * {@code coronapoker.properties}.
 */
class GamePresetRoundTripSmoke {

    @Test
    @DisplayName("serialize -> apply restores every new-game setting incl. the blind structure")
    void settingsRoundTrip() {
        double[][] ladder = {{1, 2}, {2, 4}, {5, 10}};

        GameFrame.CIEGA_PEQUEÑA = 5.0;
        GameFrame.CIEGA_GRANDE = 10.0;
        GameFrame.ACTIVE_BLIND_STRUCTURE = ladder;
        GameFrame.BUYIN = 250;
        GameFrame.FIXED_BUYIN = false;
        GameFrame.BUYIN_MIN_BB = 40;
        GameFrame.BUYIN_MAX_BB = 120;
        GameFrame.REBUY = false;
        GameFrame.REBUY_LIMIT = 3;
        GameFrame.BOT_REBUY = false;
        GameFrame.REBUY_CAP_POLICY = 1;
        GameFrame.CIEGAS_DOUBLE = 90;
        GameFrame.CIEGAS_DOUBLE_TYPE = 2;
        GameFrame.BLIND_CAP = 50.0;
        GameFrame.MANOS = 42;
        GameFrame.ANTE = true;
        GameFrame.STRADDLE = true;
        Bot.DIFFICULTY = Bot.Difficulty.EASY;

        String blob = GamePreset.serializeCurrentSettings();

        // Wipe to clearly different values, then prove apply() brings the snapshot back.
        GameFrame.CIEGA_PEQUEÑA = 0.10;
        GameFrame.CIEGA_GRANDE = 0.20;
        GameFrame.ACTIVE_BLIND_STRUCTURE = null;
        GameFrame.BUYIN = 10;
        GameFrame.FIXED_BUYIN = true;
        GameFrame.BUYIN_MIN_BB = 1;
        GameFrame.BUYIN_MAX_BB = 1;
        GameFrame.REBUY = true;
        GameFrame.REBUY_LIMIT = 0;
        GameFrame.BOT_REBUY = true;
        GameFrame.REBUY_CAP_POLICY = 0;
        GameFrame.CIEGAS_DOUBLE = 60;
        GameFrame.CIEGAS_DOUBLE_TYPE = 1;
        GameFrame.BLIND_CAP = 0;
        GameFrame.MANOS = -1;
        GameFrame.ANTE = false;
        GameFrame.STRADDLE = false;
        Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;

        new GamePreset("favorita", blob).apply();

        assertEquals(5.0, GameFrame.CIEGA_PEQUEÑA, 1e-9, "small blind");
        assertEquals(10.0, GameFrame.CIEGA_GRANDE, 1e-9, "big blind");
        assertArrayEquals(ladder, GameFrame.ACTIVE_BLIND_STRUCTURE, "chosen blind structure");
        assertEquals(250, GameFrame.BUYIN, "buy-in");
        assertFalse(GameFrame.FIXED_BUYIN, "variable buy-in");
        assertEquals(40, GameFrame.BUYIN_MIN_BB, "buy-in min bb");
        assertEquals(120, GameFrame.BUYIN_MAX_BB, "buy-in max bb");
        assertFalse(GameFrame.REBUY, "rebuy off");
        assertEquals(3, GameFrame.REBUY_LIMIT, "rebuy limit");
        assertFalse(GameFrame.BOT_REBUY, "bot rebuy off");
        assertEquals(1, GameFrame.REBUY_CAP_POLICY, "rebuy cap policy");
        assertEquals(90, GameFrame.CIEGAS_DOUBLE, "blind increase");
        assertEquals(2, GameFrame.CIEGAS_DOUBLE_TYPE, "blind increase type");
        assertEquals(50.0, GameFrame.BLIND_CAP, 1e-9, "blind cap");
        assertEquals(42, GameFrame.MANOS, "hand limit");
        assertTrue(GameFrame.ANTE, "ante on");
        assertTrue(GameFrame.STRADDLE, "straddle on");
        assertEquals(Bot.Difficulty.EASY, Bot.DIFFICULTY, "bot difficulty");
    }

    @Test
    @DisplayName("the default ladder (null structure) round-trips as null, not as an empty array")
    void defaultStructureRoundTrips() {
        GameFrame.ACTIVE_BLIND_STRUCTURE = null;
        String blob = GamePreset.serializeCurrentSettings();

        GameFrame.ACTIVE_BLIND_STRUCTURE = new double[][]{{1, 2}};
        GamePreset.applySettings(blob);

        assertNull(GameFrame.ACTIVE_BLIND_STRUCTURE, "empty STRUCT must restore the default ladder (null)");
    }

    @Test
    @DisplayName("the registry preserves names, settings and order across write -> read")
    void registryRoundTrips() {
        List<GamePreset> presets = new ArrayList<>();
        presets.add(new GamePreset("Cash 0.10/0.20", "SB=0.1#BG=0.2#ANTE=0"));
        presets.add(new GamePreset("Torneo turbo", "SB=5#BG=10#ANTE=1#STR=1"));

        Properties props = new Properties();
        GamePreset.writeTo(props, presets);
        LinkedHashMap<String, GamePreset> read = GamePreset.readFrom(props);

        assertEquals(2, read.size(), "both presets must survive");
        List<String> order = new ArrayList<>(read.keySet());
        assertEquals("Cash 0.10/0.20", order.get(0), "stored order preserved (first)");
        assertEquals("Torneo turbo", order.get(1), "stored order preserved (second)");
        assertEquals("SB=5#BG=10#ANTE=1#STR=1", read.get("Torneo turbo").getSettings(), "settings blob preserved");
    }

    @Test
    @DisplayName("rewriting fewer presets drops the stale entries (delete persists)")
    void rewriteDropsStaleEntries() {
        Properties props = new Properties();
        List<GamePreset> three = Arrays.asList(
                new GamePreset("a", "SB=1#BG=2"),
                new GamePreset("b", "SB=2#BG=4"),
                new GamePreset("c", "SB=5#BG=10"));
        GamePreset.writeTo(props, three);

        // Simulate deleting two: rewrite with just one.
        GamePreset.writeTo(props, Arrays.asList(new GamePreset("b", "SB=2#BG=4")));
        LinkedHashMap<String, GamePreset> read = GamePreset.readFrom(props);

        assertEquals(1, read.size(), "only the surviving preset must remain");
        assertTrue(read.containsKey("b"), "the kept preset must be 'b'");
        assertNull(props.getProperty(GamePreset.PROP_PREFIX + "2.name"), "no stale third entry may linger");
    }

    @Test
    @DisplayName("a corrupt entry is skipped, not fatal, and the rest still apply")
    void defensiveParseSkipsGarbage() {
        GameFrame.BUYIN = 999;
        GameFrame.REBUY = false;
        // BUYIN is unparseable, REBUY is fine: BUYIN keeps its old value, REBUY applies.
        GamePreset.applySettings("BUYIN=not_a_number#REBUY=1#ZZZ=garbage");

        assertEquals(999, GameFrame.BUYIN, "an unparseable value must leave the field untouched");
        assertTrue(GameFrame.REBUY, "a valid later entry must still apply after a bad one");
    }

    @Test
    @DisplayName("the legacy EXPERT difficulty maps to HARD")
    void legacyExpertDifficultyMapsToHard() {
        Bot.DIFFICULTY = Bot.Difficulty.EASY;
        GamePreset.applySettings("DIFF=EXPERT");
        assertEquals(Bot.Difficulty.HARD, Bot.DIFFICULTY, "legacy EXPERT must fold into HARD");
    }

    @Test
    @DisplayName("a blank preset name is rejected")
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GamePreset("   ", "SB=1#BG=2"));
        assertThrows(IllegalArgumentException.class, () -> new GamePreset(null, "SB=1#BG=2"));
    }
}
