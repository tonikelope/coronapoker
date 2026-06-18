/*
 * Copyright (C) 2020 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The persistent, user-defined blind-structure model: serialization round-trip,
 * the built-in default ladder, the validation guards (which keep a custom
 * structure sane for the escalation/cap engine), and the registry persistence
 * over a Properties object (skipping corruption, persisting deletions, leaving
 * unrelated keys untouched).
 */
public class BlindStructureTest {

    private static float[][] levels(float... sbBbPairs) {
        float[][] out = new float[sbBbPairs.length / 2][];
        for (int i = 0; i < out.length; i++) {
            out[i] = new float[]{sbBbPairs[2 * i], sbBbPairs[2 * i + 1]};
        }
        return out;
    }

    // ----- Default ladder -----------------------------------------------------

    @Test
    void defaultLadderHasTwentyLevelsAllDoubleBlind() {
        // Arrange / Act
        float[][] def = BlindStructure.defaultLevels();

        // Assert: matches the twenty hard-coded combo entries, bb = 2*sb.
        assertEquals(20, def.length);
        assertArrayEquals(new float[]{0.1f, 0.2f}, def[0], 0f);
        assertArrayEquals(new float[]{0.5f, 1f}, def[3], 0f);
        assertArrayEquals(new float[]{1f, 2f}, def[4], 0f);
        assertArrayEquals(new float[]{5000f, 10000f}, def[19], 0f);
        for (float[] lvl : def) {
            assertEquals(lvl[0] * 2f, lvl[1], 0f);
        }
    }

    @Test
    void defaultLadderIsAValidStructure() {
        assertNull(BlindStructure.validateLevels(BlindStructure.defaultLevels()));
        BlindStructure bs = new BlindStructure("Default copy", BlindStructure.defaultLevels());
        assertEquals(20, bs.size());
    }

    @Test
    void defaultLadderReturnsAFreshArrayEachCall() {
        float[][] a = BlindStructure.defaultLevels();
        a[0][0] = 999f;
        assertEquals(0.1f, BlindStructure.defaultLevels()[0][0], 0f);
    }

    // ----- Serialization round-trip -------------------------------------------

    @Test
    void levelsStringRoundTripsWholeAndFractionalValues() {
        // Arrange: mix of fractional and whole blinds, non-2x big blind.
        float[][] in = levels(0.5f, 1f, 1f, 2f, 25f, 50f, 100f, 250f);

        // Act
        String csv = BlindStructure.levelsToString(in);
        float[][] out = BlindStructure.parseLevels(csv);

        // Assert: compact form, exact round-trip.
        assertEquals("0.5/1,1/2,25/50,100/250", csv);
        assertArrayEquals(in, out);
    }

    @Test
    void formatLevelValueNeverAbbreviatesLargeBlinds() {
        // B1 regression guard: Helpers.float2String renders >=1000 as "1K"/"5K",
        // which the Float.valueOf combo parsers cannot read (NumberFormatException
        // on create / edit-blinds / UPDATEBLINDS broadcast). formatLevelValue must
        // emit a plain, parseable number for any magnitude.
        for (float v : new float[]{1000f, 2000f, 5000f, 10000f, 3000f}) {
            String s = BlindStructure.formatLevelValue(v);
            assertFalse(s.toLowerCase().contains("k"), s);
            assertEquals(v, Float.parseFloat(s.replace(",", ".")), 0f);
        }
        // A full tournament level round-trips through the "small / big" form.
        String lvl = BlindStructure.formatLevel(1000f, 2000f);
        assertEquals("1000 / 2000", lvl);
        String[] parts = lvl.replace(",", ".").split("/");
        assertEquals(1000f, Float.parseFloat(parts[0].trim()), 0f);
        assertEquals(2000f, Float.parseFloat(parts[1].trim()), 0f);
    }

    @Test
    void levelsStringIsSafeForTheInitWire() {
        // The INIT command is '#'-delimited with '@' sub-delimiters; the serialized
        // ladder must never contain either or it would corrupt the wire framing.
        for (float[][] ladder : new float[][][]{
            BlindStructure.defaultLevels(),
            levels(0.1f, 0.2f, 0.5f, 1f),
            levels(10f, 25f, 20f, 50f, 40f, 100f)}) {
            String csv = BlindStructure.levelsToString(ladder);
            assertFalse(csv.contains("#"), csv);
            assertFalse(csv.contains("@"), csv);
            assertArrayEquals(ladder, BlindStructure.parseLevels(csv));
        }
    }

    @Test
    void levelsStringRoundTripsNickelBlinds() {
        float[][] in = levels(0.05f, 0.10f, 0.25f, 0.50f, 0.35f, 0.70f);
        String csv = BlindStructure.levelsToString(in);
        assertArrayEquals(in, BlindStructure.parseLevels(csv));
    }

    @Test
    void parseLevelsToleratesWhitespace() {
        float[][] out = BlindStructure.parseLevels(" 25/50 , 50/100 ");
        assertArrayEquals(levels(25f, 50f, 50f, 100f), out);
    }

    @Test
    void parseLevelsRejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> BlindStructure.parseLevels(""));
        assertThrows(IllegalArgumentException.class, () -> BlindStructure.parseLevels("25"));
        assertThrows(IllegalArgumentException.class, () -> BlindStructure.parseLevels("25/50/100"));
        assertThrows(IllegalArgumentException.class, () -> BlindStructure.parseLevels("abc/def"));
    }

    @Test
    void parseValidatedLevelsAcceptsAValidLadder() {
        // Grammar-valid AND logically valid (incl. a non-2x level) round-trips.
        assertArrayEquals(levels(0.10f, 0.25f, 0.20f, 0.50f),
                BlindStructure.parseValidatedLevels("0.1/0.25,0.2/0.5"));
    }

    @Test
    void parseValidatedLevelsRejectsGrammarValidButLogicallyInvalidLadders() {
        // These all PARSE (grammar ok) but must be rejected by the validation gate,
        // so a corrupt persisted/wire ladder can never reach the engine.
        assertThrows(IllegalArgumentException.class,
                () -> BlindStructure.parseValidatedLevels("0.50/0.25")); // bb < sb
        assertThrows(IllegalArgumentException.class,
                () -> BlindStructure.parseValidatedLevels("0.10/0.20,0.10/0.20")); // not increasing
        assertThrows(IllegalArgumentException.class,
                () -> BlindStructure.parseValidatedLevels("0.04/0.08")); // off the 0.05 step
        assertThrows(IllegalArgumentException.class,
                () -> BlindStructure.parseValidatedLevels("0.33/0.66")); // off the 0.05 step
        // And it still rejects pure grammar garbage.
        assertThrows(IllegalArgumentException.class,
                () -> BlindStructure.parseValidatedLevels("abc/def"));
    }

    // ----- Name validation ----------------------------------------------------

    @Test
    void nameValidation() {
        assertNull(BlindStructure.validateName("Casa"));
        assertNull(BlindStructure.validateName("  Turbo 6-max  ")); // trimmed, ok
        assertEquals(BlindStructure.ERR_NAME_EMPTY, BlindStructure.validateName(""));
        assertEquals(BlindStructure.ERR_NAME_EMPTY, BlindStructure.validateName("   "));
        assertEquals(BlindStructure.ERR_NAME_EMPTY, BlindStructure.validateName(null));
        assertEquals(BlindStructure.ERR_NAME_CHARS, BlindStructure.validateName("bad\nname"));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i <= BlindStructure.MAX_NAME_LENGTH; i++) {
            tooLong.append('x');
        }
        assertEquals(BlindStructure.ERR_NAME_TOO_LONG, BlindStructure.validateName(tooLong.toString()));
    }

    // ----- Level validation ---------------------------------------------------

    @Test
    void levelsValidationAcceptsSaneLadders() {
        assertNull(BlindStructure.validateLevels(levels(25f, 50f, 50f, 100f, 100f, 200f)));
        // Single fixed level is allowed (escalation simply never moves).
        assertNull(BlindStructure.validateLevels(levels(100f, 200f)));
        // Non-2x big blind is allowed (total-freedom mode).
        assertNull(BlindStructure.validateLevels(levels(10f, 25f, 20f, 50f)));
        // Big blind equal to small blind is allowed (bb >= sb).
        assertNull(BlindStructure.validateLevels(levels(10f, 10f, 20f, 20f)));
    }

    @Test
    void levelsValidationRejectsEmptyAndOversized() {
        assertEquals(BlindStructure.ERR_NO_LEVELS, BlindStructure.validateLevels(null));
        assertEquals(BlindStructure.ERR_NO_LEVELS, BlindStructure.validateLevels(new float[0][]));
        float[][] tooMany = new float[BlindStructure.MAX_LEVELS + 1][];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = new float[]{i + 1, (i + 1) * 2};
        }
        assertEquals(BlindStructure.ERR_TOO_MANY_LEVELS, BlindStructure.validateLevels(tooMany));
    }

    @Test
    void levelsValidationAcceptsNickelGranularBlinds() {
        // Blinds in 0.05 steps, minimum 0.05 (0.25/0.35 allowed, 0.33 not).
        assertNull(BlindStructure.validateLevels(levels(0.05f, 0.10f)));
        assertNull(BlindStructure.validateLevels(levels(0.25f, 0.50f, 0.50f, 1.00f)));
        assertNull(BlindStructure.validateLevels(levels(0.35f, 0.70f)));
        assertNull(BlindStructure.validateLevels(levels(0.15f, 0.30f)));
    }

    @Test
    void levelsValidationRejectsOutOfRangeAndPrecision() {
        assertEquals(BlindStructure.ERR_VALUE_RANGE, BlindStructure.validateLevels(levels(0f, 0f)));
        assertEquals(BlindStructure.ERR_VALUE_RANGE,
                BlindStructure.validateLevels(levels(0.04f, 0.10f))); // below MIN_BLIND (0.05)
        assertEquals(BlindStructure.ERR_PRECISION,
                BlindStructure.validateLevels(levels(0.33f, 0.66f))); // not a 0.05 multiple
        assertEquals(BlindStructure.ERR_PRECISION,
                BlindStructure.validateLevels(levels(0.07f, 0.20f))); // not a 0.05 multiple
    }

    @Test
    void levelsValidationRejectsBbBelowSb() {
        assertEquals(BlindStructure.ERR_BB_LT_SB, BlindStructure.validateLevels(levels(50f, 25f)));
    }

    @Test
    void levelsValidationRejectsNonIncreasingLadder() {
        // Small blind not climbing.
        assertEquals(BlindStructure.ERR_NOT_INCREASING,
                BlindStructure.validateLevels(levels(50f, 100f, 50f, 200f)));
        // Big blind not climbing.
        assertEquals(BlindStructure.ERR_NOT_INCREASING,
                BlindStructure.validateLevels(levels(50f, 100f, 60f, 100f)));
        // Duplicate level.
        assertEquals(BlindStructure.ERR_NOT_INCREASING,
                BlindStructure.validateLevels(levels(50f, 100f, 50f, 100f)));
    }

    @Test
    void constructorThrowsOnInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new BlindStructure("", levels(25f, 50f)));
        assertThrows(IllegalArgumentException.class, () -> new BlindStructure("ok", levels(50f, 25f)));
    }

    @Test
    void getLevelsIsADefensiveCopy() {
        BlindStructure bs = new BlindStructure("Casa", levels(25f, 50f, 50f, 100f));
        float[][] got = bs.getLevels();
        got[0][0] = 999f;
        assertEquals(25f, bs.getLevels()[0][0], 0f);
    }

    // ----- Registry persistence (pure, over a Properties object) --------------

    @Test
    void registryRoundTripPreservesOrderAndValues() {
        // Arrange
        List<BlindStructure> in = new ArrayList<>();
        in.add(new BlindStructure("Casa", levels(25f, 50f, 50f, 100f, 100f, 200f)));
        in.add(new BlindStructure("Turbo", levels(50f, 100f, 100f, 200f)));
        Properties props = new Properties();

        // Act
        BlindStructure.writeTo(props, in);
        LinkedHashMap<String, BlindStructure> out = BlindStructure.readFrom(props);

        // Assert: same names, same order, same values.
        assertEquals(2, out.size());
        assertEquals(List.of("Casa", "Turbo"), new ArrayList<>(out.keySet()));
        assertEquals(in.get(0), out.get("Casa"));
        assertEquals(in.get(1), out.get("Turbo"));
        assertEquals("2", props.getProperty(BlindStructure.PROP_COUNT));
    }

    @Test
    void writeToReplacesPreviousSetAndLeavesUnrelatedKeys() {
        // Arrange: a properties file with a prior, larger set plus an unrelated key.
        Properties props = new Properties();
        props.setProperty("zoom_menu", "1.25");
        BlindStructure.writeTo(props, List.of(
                new BlindStructure("Old1", levels(25f, 50f)),
                new BlindStructure("Old2", levels(50f, 100f)),
                new BlindStructure("Old3", levels(75f, 150f))));

        // Act: overwrite with a smaller set (a deletion).
        BlindStructure.writeTo(props, List.of(new BlindStructure("Kept", levels(10f, 20f))));
        LinkedHashMap<String, BlindStructure> out = BlindStructure.readFrom(props);

        // Assert: only the new set survives; the deleted structures' keys are gone;
        // the unrelated preference is untouched.
        assertEquals(1, out.size());
        assertTrue(out.containsKey("Kept"));
        assertEquals("1", props.getProperty(BlindStructure.PROP_COUNT));
        assertNull(props.getProperty(BlindStructure.PROP_PREFIX + "1.name"));
        assertNull(props.getProperty(BlindStructure.PROP_PREFIX + "2.name"));
        assertEquals("1.25", props.getProperty("zoom_menu"));
    }

    @Test
    void readFromSkipsCorruptEntriesWithoutFailing() {
        // Arrange: count says 3 but the middle one has an invalid ladder.
        Properties props = new Properties();
        props.setProperty(BlindStructure.PROP_COUNT, "3");
        props.setProperty(BlindStructure.PROP_PREFIX + "0.name", "Good");
        props.setProperty(BlindStructure.PROP_PREFIX + "0.levels", "25/50,50/100");
        props.setProperty(BlindStructure.PROP_PREFIX + "1.name", "Broken");
        props.setProperty(BlindStructure.PROP_PREFIX + "1.levels", "50/25"); // bb < sb
        props.setProperty(BlindStructure.PROP_PREFIX + "2.name", "AlsoGood");
        props.setProperty(BlindStructure.PROP_PREFIX + "2.levels", "100/200");

        // Act
        LinkedHashMap<String, BlindStructure> out = BlindStructure.readFrom(props);

        // Assert: the two valid ones survive, the corrupt one is skipped.
        assertEquals(2, out.size());
        assertTrue(out.containsKey("Good"));
        assertTrue(out.containsKey("AlsoGood"));
        assertFalse(out.containsKey("Broken"));
    }

    @Test
    void readFromSkipsDuplicateNames() {
        Properties props = new Properties();
        props.setProperty(BlindStructure.PROP_COUNT, "2");
        props.setProperty(BlindStructure.PROP_PREFIX + "0.name", "Casa");
        props.setProperty(BlindStructure.PROP_PREFIX + "0.levels", "25/50");
        props.setProperty(BlindStructure.PROP_PREFIX + "1.name", "Casa");
        props.setProperty(BlindStructure.PROP_PREFIX + "1.levels", "50/100");

        LinkedHashMap<String, BlindStructure> out = BlindStructure.readFrom(props);

        assertEquals(1, out.size());
        // First wins.
        assertArrayEquals(levels(25f, 50f), out.get("Casa").getLevels());
    }

    @Test
    void readFromEmptyOrAbsentIsEmpty() {
        assertTrue(BlindStructure.readFrom(new Properties()).isEmpty());
        assertTrue(BlindStructure.readFrom(null).isEmpty());
        Properties garbage = new Properties();
        garbage.setProperty(BlindStructure.PROP_COUNT, "not-a-number");
        assertTrue(BlindStructure.readFrom(garbage).isEmpty());
    }

    @Test
    void readFromClampsAbsurdCount() {
        // count far beyond MAX_STRUCTURES must not blow up or over-read.
        Properties props = new Properties();
        props.setProperty(BlindStructure.PROP_COUNT, "100000");
        props.setProperty(BlindStructure.PROP_PREFIX + "0.name", "Only");
        props.setProperty(BlindStructure.PROP_PREFIX + "0.levels", "25/50");
        LinkedHashMap<String, BlindStructure> out = BlindStructure.readFrom(props);
        assertEquals(1, out.size());
        assertNotNull(out.get("Only"));
    }

    // ----- Escalation walk (pure helpers used by Crupier custom path) ---------

    @Test
    void indexOfLevelFindsByCentResolution() {
        // Cent resolution distinguishes 0.25 from 0.20/0.30 (needed for 0.05 blinds).
        float[][] s = levels(0.1f, 0.2f, 0.25f, 0.5f, 0.5f, 1f, 25f, 50f);
        assertEquals(0, BlindStructure.indexOfLevel(s, 0.1f));
        assertEquals(1, BlindStructure.indexOfLevel(s, 0.25f));
        assertEquals(2, BlindStructure.indexOfLevel(s, 0.5f));
        assertEquals(3, BlindStructure.indexOfLevel(s, 25f));
        // Tolerant of float drift (rounds to the same cent).
        assertEquals(1, BlindStructure.indexOfLevel(s, 0.250001f));
        // Off the ladder (0.27 is not a level).
        assertEquals(-1, BlindStructure.indexOfLevel(s, 0.27f));
        assertEquals(-1, BlindStructure.indexOfLevel(null, 1f));
    }

    @Test
    void nextLevelWalksThenCapsAtTop() {
        float[][] s = levels(25f, 50f, 50f, 100f, 100f, 200f);
        assertArrayEquals(new float[]{50f, 100f}, BlindStructure.nextLevel(s, 25f), 0f);
        assertArrayEquals(new float[]{100f, 200f}, BlindStructure.nextLevel(s, 50f), 0f);
        // Top level: no further escalation.
        assertNull(BlindStructure.nextLevel(s, 100f));
        // Off the ladder.
        assertNull(BlindStructure.nextLevel(s, 999f));
    }

    @Test
    void nextLevelDrivesAFullDeterministicWalk() {
        // Mirrors how Crupier.doblarCiegas climbs a custom structure: start at the
        // first level and follow nextLevel until it caps, collecting the ladder.
        float[][] s = levels(10f, 25f, 20f, 50f, 40f, 100f); // non-2x big blinds
        ArrayList<float[]> walked = new ArrayList<>();
        walked.add(s[0]);
        float sb = s[0][0];
        float[] next;
        while ((next = BlindStructure.nextLevel(s, sb)) != null) {
            walked.add(next);
            sb = next[0];
        }
        assertArrayEquals(s, walked.toArray(new float[0][]));
    }

    @Test
    void singleLevelStructureNeverEscalates() {
        float[][] s = levels(100f, 200f);
        assertNull(BlindStructure.nextLevel(s, 100f));
    }

    @Test
    void defaultLadderNextLevelCapsAtTop() {
        // The helper caps the built-in ladder at 5000/10000; in-engine the default
        // (null) path keeps its infinite-by-decade behaviour, this only documents
        // the helper's contract for when a default-derived structure is walked.
        float[][] def = BlindStructure.defaultLevels();
        assertArrayEquals(new float[]{0.2f, 0.4f}, BlindStructure.nextLevel(def, 0.1f), 0f);
        assertArrayEquals(new float[]{2f, 4f}, BlindStructure.nextLevel(def, 1f), 0f);
        assertNull(BlindStructure.nextLevel(def, 5000f));
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        BlindStructure a = new BlindStructure("Casa", levels(25f, 50f, 50f, 100f));
        BlindStructure b = new BlindStructure("Casa", levels(25f, 50f, 50f, 100f));
        BlindStructure c = new BlindStructure("Casa", levels(25f, 50f));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));
    }
}
