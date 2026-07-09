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

    private static double[][] levels(double... sbBbPairs) {
        double[][] out = new double[sbBbPairs.length / 2][];
        for (int i = 0; i < out.length; i++) {
            out[i] = new double[]{sbBbPairs[2 * i], sbBbPairs[2 * i + 1]};
        }
        return out;
    }

    // ----- Default ladder -----------------------------------------------------

    @Test
    void defaultLadderHasTwentyLevelsAllDoubleBlind() {
        // Arrange / Act
        double[][] def = BlindStructure.defaultLevels();

        // Assert: matches the twenty hard-coded combo entries, bb = 2*sb.
        assertEquals(20, def.length);
        assertArrayEquals(new double[]{0.1, 0.2}, def[0], 0);
        assertArrayEquals(new double[]{0.5, 1}, def[3], 0);
        assertArrayEquals(new double[]{1, 2}, def[4], 0);
        assertArrayEquals(new double[]{5000, 10000}, def[19], 0);
        for (double[] lvl : def) {
            assertEquals(lvl[0] * 2, lvl[1], 0);
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
        double[][] a = BlindStructure.defaultLevels();
        a[0][0] = 999;
        assertEquals(0.1, BlindStructure.defaultLevels()[0][0], 0);
    }

    // ----- Serialization round-trip -------------------------------------------

    @Test
    void levelsStringRoundTripsWholeAndFractionalValues() {
        // Arrange: mix of fractional and whole blinds, non-2x big blind.
        double[][] in = levels(0.5, 1, 1, 2, 25, 50, 100, 250);

        // Act
        String csv = BlindStructure.levelsToString(in);
        double[][] out = BlindStructure.parseLevels(csv);

        // Assert: compact form, exact round-trip.
        assertEquals("0.5/1,1/2,25/50,100/250", csv);
        assertArrayEquals(in, out);
    }

    @Test
    void formatLevelValueNeverAbbreviatesLargeBlinds() {
        // B1 regression guard: Helpers.double2String renders >=1000 as "1K"/"5K",
        // which the Float.valueOf combo parsers cannot read (NumberFormatException
        // on create / edit-blinds / UPDATEBLINDS broadcast). formatLevelValue must
        // emit a plain, parseable number for any magnitude.
        for (double v : new double[]{1000, 2000, 5000, 10000, 3000}) {
            String s = BlindStructure.formatLevelValue(v);
            assertFalse(s.toLowerCase().contains("k"), s);
            assertEquals(v, Float.parseFloat(s.replace(",", ".")), 0);
        }
        // A full tournament level round-trips through the "small / big" form.
        String lvl = BlindStructure.formatLevel(1000, 2000);
        assertEquals("1000 / 2000", lvl);
        String[] parts = lvl.replace(",", ".").split("/");
        assertEquals(1000, Float.parseFloat(parts[0].trim()), 0);
        assertEquals(2000, Float.parseFloat(parts[1].trim()), 0);
    }

    @Test
    void levelsStringIsSafeForTheInitWire() {
        // The INIT command is '#'-delimited with '@' sub-delimiters; the serialized
        // ladder must never contain either or it would corrupt the wire framing.
        for (double[][] ladder : new double[][][]{
            BlindStructure.defaultLevels(),
            levels(0.1, 0.2, 0.5, 1),
            levels(10, 25, 20, 50, 40, 100)}) {
            String csv = BlindStructure.levelsToString(ladder);
            assertFalse(csv.contains("#"), csv);
            assertFalse(csv.contains("@"), csv);
            assertArrayEquals(ladder, BlindStructure.parseLevels(csv));
        }
    }

    @Test
    void levelsStringRoundTripsNickelBlinds() {
        double[][] in = levels(0.05, 0.10, 0.25, 0.50, 0.35, 0.70);
        String csv = BlindStructure.levelsToString(in);
        assertArrayEquals(in, BlindStructure.parseLevels(csv));
    }

    @Test
    void parseLevelsToleratesWhitespace() {
        double[][] out = BlindStructure.parseLevels(" 25/50 , 50/100 ");
        assertArrayEquals(levels(25, 50, 50, 100), out);
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
        assertArrayEquals(levels(0.10, 0.25, 0.20, 0.50),
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
        assertNull(BlindStructure.validateLevels(levels(25, 50, 50, 100, 100, 200)));
        // Single fixed level is allowed (escalation simply never moves).
        assertNull(BlindStructure.validateLevels(levels(100, 200)));
        // Non-2x big blind is allowed (total-freedom mode).
        assertNull(BlindStructure.validateLevels(levels(10, 25, 20, 50)));
        // Big blind equal to small blind is allowed (bb >= sb).
        assertNull(BlindStructure.validateLevels(levels(10, 10, 20, 20)));
    }

    @Test
    void levelsValidationRejectsEmptyAndOversized() {
        assertEquals(BlindStructure.ERR_NO_LEVELS, BlindStructure.validateLevels(null));
        assertEquals(BlindStructure.ERR_NO_LEVELS, BlindStructure.validateLevels(new double[0][]));
        double[][] tooMany = new double[BlindStructure.MAX_LEVELS + 1][];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = new double[]{i + 1, (i + 1) * 2};
        }
        assertEquals(BlindStructure.ERR_TOO_MANY_LEVELS, BlindStructure.validateLevels(tooMany));
    }

    @Test
    void levelsValidationAcceptsNickelGranularBlinds() {
        // Blinds in 0.05 steps, minimum 0.05 (0.25/0.35 allowed, 0.33 not).
        assertNull(BlindStructure.validateLevels(levels(0.05, 0.10)));
        assertNull(BlindStructure.validateLevels(levels(0.25, 0.50, 0.50, 1.00)));
        assertNull(BlindStructure.validateLevels(levels(0.35, 0.70)));
        assertNull(BlindStructure.validateLevels(levels(0.15, 0.30)));
    }

    @Test
    void levelsValidationRejectsOutOfRangeAndPrecision() {
        assertEquals(BlindStructure.ERR_VALUE_RANGE, BlindStructure.validateLevels(levels(0, 0)));
        assertEquals(BlindStructure.ERR_VALUE_RANGE,
                BlindStructure.validateLevels(levels(0.04, 0.10))); // below MIN_BLIND (0.05)
        assertEquals(BlindStructure.ERR_PRECISION,
                BlindStructure.validateLevels(levels(0.33, 0.66))); // not a 0.05 multiple
        assertEquals(BlindStructure.ERR_PRECISION,
                BlindStructure.validateLevels(levels(0.07, 0.20))); // not a 0.05 multiple
    }

    @Test
    void levelsValidationRejectsBbBelowSb() {
        assertEquals(BlindStructure.ERR_BB_LT_SB, BlindStructure.validateLevels(levels(50, 25)));
    }

    @Test
    void levelsValidationRejectsNonIncreasingLadder() {
        // Small blind not climbing.
        assertEquals(BlindStructure.ERR_NOT_INCREASING,
                BlindStructure.validateLevels(levels(50, 100, 50, 200)));
        // Big blind not climbing.
        assertEquals(BlindStructure.ERR_NOT_INCREASING,
                BlindStructure.validateLevels(levels(50, 100, 60, 100)));
        // Duplicate level.
        assertEquals(BlindStructure.ERR_NOT_INCREASING,
                BlindStructure.validateLevels(levels(50, 100, 50, 100)));
    }

    @Test
    void constructorThrowsOnInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new BlindStructure("", levels(25, 50)));
        assertThrows(IllegalArgumentException.class, () -> new BlindStructure("ok", levels(50, 25)));
    }

    @Test
    void getLevelsIsADefensiveCopy() {
        BlindStructure bs = new BlindStructure("Casa", levels(25, 50, 50, 100));
        double[][] got = bs.getLevels();
        got[0][0] = 999;
        assertEquals(25, bs.getLevels()[0][0], 0);
    }

    // ----- Registry persistence (pure, over a Properties object) --------------

    @Test
    void registryRoundTripPreservesOrderAndValues() {
        // Arrange
        List<BlindStructure> in = new ArrayList<>();
        in.add(new BlindStructure("Casa", levels(25, 50, 50, 100, 100, 200)));
        in.add(new BlindStructure("Turbo", levels(50, 100, 100, 200)));
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
                new BlindStructure("Old1", levels(25, 50)),
                new BlindStructure("Old2", levels(50, 100)),
                new BlindStructure("Old3", levels(75, 150))));

        // Act: overwrite with a smaller set (a deletion).
        BlindStructure.writeTo(props, List.of(new BlindStructure("Kept", levels(10, 20))));
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
        assertArrayEquals(levels(25, 50), out.get("Casa").getLevels());
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
        double[][] s = levels(0.1, 0.2, 0.25, 0.5, 0.5, 1, 25, 50);
        assertEquals(0, BlindStructure.indexOfLevel(s, 0.1));
        assertEquals(1, BlindStructure.indexOfLevel(s, 0.25));
        assertEquals(2, BlindStructure.indexOfLevel(s, 0.5));
        assertEquals(3, BlindStructure.indexOfLevel(s, 25));
        // Tolerant of double drift (rounds to the same cent).
        assertEquals(1, BlindStructure.indexOfLevel(s, 0.250001));
        // Off the ladder (0.27 is not a level).
        assertEquals(-1, BlindStructure.indexOfLevel(s, 0.27));
        assertEquals(-1, BlindStructure.indexOfLevel(null, 1));
    }

    @Test
    void nextLevelWalksThenCapsAtTop() {
        double[][] s = levels(25, 50, 50, 100, 100, 200);
        assertArrayEquals(new double[]{50, 100}, BlindStructure.nextLevel(s, 25), 0);
        assertArrayEquals(new double[]{100, 200}, BlindStructure.nextLevel(s, 50), 0);
        // Top level: no further escalation.
        assertNull(BlindStructure.nextLevel(s, 100));
        // Off the ladder.
        assertNull(BlindStructure.nextLevel(s, 999));
    }

    @Test
    void nextLevelDrivesAFullDeterministicWalk() {
        // Mirrors how Crupier.doblarCiegas climbs a custom structure: start at the
        // first level and follow nextLevel until it caps, collecting the ladder.
        double[][] s = levels(10, 25, 20, 50, 40, 100); // non-2x big blinds
        ArrayList<double[]> walked = new ArrayList<>();
        walked.add(s[0]);
        double sb = s[0][0];
        double[] next;
        while ((next = BlindStructure.nextLevel(s, sb)) != null) {
            walked.add(next);
            sb = next[0];
        }
        assertArrayEquals(s, walked.toArray(new double[0][]));
    }

    @Test
    void singleLevelStructureNeverEscalates() {
        double[][] s = levels(100, 200);
        assertNull(BlindStructure.nextLevel(s, 100));
    }

    @Test
    void defaultLadderNextLevelCapsAtTop() {
        // The helper caps the built-in ladder at 5000/10000. In-engine, the default
        // (null) escalation path walks this very ladder (Crupier.effectiveBlindStructure
        // falls back to defaultLevels), so the blinds stop climbing at the top level
        // instead of running away by decades.
        double[][] def = BlindStructure.defaultLevels();
        assertArrayEquals(new double[]{0.2, 0.4}, BlindStructure.nextLevel(def, 0.1), 0);
        assertArrayEquals(new double[]{2, 4}, BlindStructure.nextLevel(def, 1), 0);
        assertNull(BlindStructure.nextLevel(def, 5000));
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        BlindStructure a = new BlindStructure("Casa", levels(25, 50, 50, 100));
        BlindStructure b = new BlindStructure("Casa", levels(25, 50, 50, 100));
        BlindStructure c = new BlindStructure("Casa", levels(25, 50));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));
    }
}
