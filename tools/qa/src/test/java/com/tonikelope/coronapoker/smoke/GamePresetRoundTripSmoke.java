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
 * <p>Pure logic — exercises the {@link GamePreset.Settings} carrier and a local
 * {@link Properties}, so it never reads or writes the user's real
 * {@code coronapoker.properties} and never mutates global game state.
 */
class GamePresetRoundTripSmoke {

    @Test
    @DisplayName("serialize -> parse restores every new-game setting incl. the blind structure")
    void settingsRoundTrip() {
        double[][] ladder = {{1, 2}, {2, 4}, {5, 10}};

        GamePreset.Settings s = new GamePreset.Settings();
        s.smallBlind = 5.0;
        s.bigBlind = 10.0;
        s.structure = ladder;
        s.buyin = 250;
        s.fixedBuyin = false;
        s.minBb = 40;
        s.maxBb = 120;
        s.rebuy = false;
        s.rebuyLimit = 3;
        s.botRebuy = false;
        s.rebuyCapPolicy = 1;
        s.doubleEvery = 90;
        s.doubleType = 2;
        s.blindCap = 50.0;
        s.handLimit = 42;
        s.ante = true;
        s.straddle = true;
        s.difficulty = Bot.Difficulty.EASY;

        GamePreset.Settings r = GamePreset.Settings.parse(s.serialize());

        assertEquals(5.0, r.smallBlind, 1e-9, "small blind");
        assertEquals(10.0, r.bigBlind, 1e-9, "big blind");
        assertArrayEquals(ladder, r.structure, "chosen blind structure");
        assertEquals(250, r.buyin, "buy-in");
        assertFalse(r.fixedBuyin, "variable buy-in");
        assertEquals(40, r.minBb, "buy-in min bb");
        assertEquals(120, r.maxBb, "buy-in max bb");
        assertFalse(r.rebuy, "rebuy off");
        assertEquals(3, r.rebuyLimit, "rebuy limit");
        assertFalse(r.botRebuy, "bot rebuy off");
        assertEquals(1, r.rebuyCapPolicy, "rebuy cap policy");
        assertEquals(90, r.doubleEvery, "blind increase");
        assertEquals(2, r.doubleType, "blind increase type");
        assertEquals(50.0, r.blindCap, 1e-9, "blind cap");
        assertEquals(42, r.handLimit, "hand limit");
        assertTrue(r.ante, "ante on");
        assertTrue(r.straddle, "straddle on");
        assertEquals(Bot.Difficulty.EASY, r.difficulty, "bot difficulty");
    }

    @Test
    @DisplayName("the default ladder (null structure) round-trips as null, not as an empty array")
    void defaultStructureRoundTrips() {
        GamePreset.Settings s = new GamePreset.Settings();
        s.structure = null;
        assertNull(GamePreset.Settings.parse(s.serialize()).structure,
                "empty STRUCT must restore the default ladder (null)");
    }

    @Test
    @DisplayName("a corrupt entry is skipped, not fatal, and the rest still parse")
    void defensiveParseSkipsGarbage() {
        // BUYIN is unparseable (keeps its default), REBUY is valid and applies.
        GamePreset.Settings r = GamePreset.Settings.parse("BUYIN=not_a_number#REBUY=0#ZZZ=garbage");
        assertEquals(10, r.buyin, "an unparseable value must leave the field at its default");
        assertFalse(r.rebuy, "a valid later entry must still parse after a bad one");
    }

    @Test
    @DisplayName("the legacy EXPERT difficulty maps to HARD")
    void legacyExpertDifficultyMapsToHard() {
        assertEquals(Bot.Difficulty.HARD, GamePreset.Settings.parse("DIFF=EXPERT").difficulty,
                "legacy EXPERT must fold into HARD");
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
        GamePreset.writeTo(props, Arrays.asList(
                new GamePreset("a", "SB=1#BG=2"),
                new GamePreset("b", "SB=2#BG=4"),
                new GamePreset("c", "SB=5#BG=10")));

        // Simulate deleting two: rewrite with just one.
        GamePreset.writeTo(props, Arrays.asList(new GamePreset("b", "SB=2#BG=4")));
        LinkedHashMap<String, GamePreset> read = GamePreset.readFrom(props);

        assertEquals(1, read.size(), "only the surviving preset must remain");
        assertTrue(read.containsKey("b"), "the kept preset must be 'b'");
        assertNull(props.getProperty(GamePreset.PROP_PREFIX + "2.name"), "no stale third entry may linger");
    }

    @Test
    @DisplayName("a blank preset name is rejected")
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GamePreset("   ", "SB=1#BG=2"));
        assertThrows(IllegalArgumentException.class, () -> new GamePreset(null, "SB=1#BG=2"));
    }
}
