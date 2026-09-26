package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.BuyinRules;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class BlindStructureRulesTest {

    @Test
    void defaultLadderMatchesTheGoldThirtyLevelStructure() {
        double[][] levels = BlindStructureRules.defaultLevels();

        assertEquals(30, levels.length);
        assertArrayEquals(new double[]{0.1, 0.2}, levels[0], 0);
        assertArrayEquals(new double[]{0.5, 1}, levels[3], 0);
        assertArrayEquals(new double[]{1, 2}, levels[4], 0);
        assertArrayEquals(new double[]{5_000, 10_000}, levels[19], 0);
        assertArrayEquals(new double[]{2_000_000, 4_000_000}, levels[29], 0);
        for (double[] level : levels) {
            assertEquals(level[0] * 2, level[1], 0);
        }
        assertTrue((long) BuyinRules.CEIL_MAX_BB * (long) levels[29][1]
                <= Integer.MAX_VALUE);
        assertNull(BlindStructureRules.validateLevels(levels));

        levels[0][0] = 999;
        assertEquals(0.1, BlindStructureRules.defaultLevels()[0][0], 0);
    }

    @Test
    void acceptsSaneFixedAndNonStandardLadders() {
        assertNull(BlindStructureRules.validateLevels(
                levels(25, 50, 50, 100, 100, 200)));
        assertNull(BlindStructureRules.validateLevels(levels(100, 200)));
        assertNull(BlindStructureRules.validateLevels(
                levels(10, 25, 20, 50)));
        assertNull(BlindStructureRules.validateLevels(
                levels(10, 10, 20, 20)));
        assertNull(BlindStructureRules.validateLevels(
                levels(0.05, 0.10, 0.25, 0.50, 0.35, 0.70)));
    }

    @Test
    void rejectsEmptyOversizedAndMalformedLadders() {
        assertEquals(BlindStructureRules.ERR_NO_LEVELS,
                BlindStructureRules.validateLevels(null));
        assertEquals(BlindStructureRules.ERR_NO_LEVELS,
                BlindStructureRules.validateLevels(new double[0][]));
        assertEquals(BlindStructureRules.ERR_NO_LEVELS,
                BlindStructureRules.validateLevels(new double[][]{null}));
        assertEquals(BlindStructureRules.ERR_NO_LEVELS,
                BlindStructureRules.validateLevels(new double[][]{{1}}));

        double[][] oversized = new double[BlindStructureRules.MAX_LEVELS + 1][];
        for (int index = 0; index < oversized.length; index++) {
            oversized[index] = new double[]{index + 1, (index + 1) * 2};
        }
        assertEquals(BlindStructureRules.ERR_TOO_MANY_LEVELS,
                BlindStructureRules.validateLevels(oversized));
    }

    @Test
    void rejectsInvalidAmountsPrecisionAndOrdering() {
        assertEquals(BlindStructureRules.ERR_VALUE_RANGE,
                BlindStructureRules.validateLevels(levels(0, 0)));
        assertEquals(BlindStructureRules.ERR_VALUE_RANGE,
                BlindStructureRules.validateLevels(levels(0.04, 0.10)));
        assertEquals(BlindStructureRules.ERR_VALUE_RANGE,
                BlindStructureRules.validateLevels(
                        levels(Double.NaN, 1)));
        assertEquals(BlindStructureRules.ERR_PRECISION,
                BlindStructureRules.validateLevels(levels(0.33, 0.66)));
        assertEquals(BlindStructureRules.ERR_PRECISION,
                BlindStructureRules.validateLevels(levels(0.07, 0.20)));
        assertEquals(BlindStructureRules.ERR_BB_LT_SB,
                BlindStructureRules.validateLevels(levels(50, 25)));
        assertEquals(BlindStructureRules.ERR_NOT_INCREASING,
                BlindStructureRules.validateLevels(
                        levels(50, 100, 50, 200)));
        assertEquals(BlindStructureRules.ERR_NOT_INCREASING,
                BlindStructureRules.validateLevels(
                        levels(50, 100, 60, 100)));
        assertEquals(BlindStructureRules.ERR_NOT_INCREASING,
                BlindStructureRules.validateLevels(
                        levels(50, 100, 50, 100)));
    }

    @Test
    void findsExactCentLevelsAndRejectsOffGridAmounts() {
        double[][] structure = levels(
                0.1, 0.2, 0.25, 0.5, 0.5, 1, 25, 50);

        assertEquals(0, BlindStructureRules.indexOfLevel(structure, 0.1));
        assertEquals(1, BlindStructureRules.indexOfLevel(structure, 0.25));
        assertEquals(2, BlindStructureRules.indexOfLevel(structure, 0.5));
        assertEquals(3, BlindStructureRules.indexOfLevel(structure, 25));
        assertThrows(IllegalArgumentException.class,
                () -> BlindStructureRules.indexOfLevel(
                        structure, 0.250001));
        assertEquals(-1,
                BlindStructureRules.indexOfLevel(structure, 0.27));
        assertEquals(-1, BlindStructureRules.indexOfLevel(null, 1));
    }

    @Test
    void nextLevelWalksTheWholeLadderAndCapsAtTheTop() {
        double[][] structure = levels(10, 25, 20, 50, 40, 100);
        ArrayList<double[]> walked = new ArrayList<>();
        walked.add(structure[0]);
        double smallBlind = structure[0][0];
        double[] next;
        while ((next = BlindStructureRules.nextLevel(
                structure, smallBlind)) != null) {
            walked.add(next);
            smallBlind = next[0];
        }

        assertArrayEquals(structure, walked.toArray(new double[0][]));
        assertNull(BlindStructureRules.nextLevel(structure, 40));
        assertNull(BlindStructureRules.nextLevel(structure, 999));

        double[][] defaults = BlindStructureRules.defaultLevels();
        assertArrayEquals(new double[]{0.2, 0.4},
                BlindStructureRules.nextLevel(defaults, 0.1), 0);
        assertArrayEquals(new double[]{2, 4},
                BlindStructureRules.nextLevel(defaults, 1), 0);
        assertArrayEquals(new double[]{10_000, 20_000},
                BlindStructureRules.nextLevel(defaults, 5_000), 0);
        assertArrayEquals(new double[]{2_000_000, 4_000_000},
                BlindStructureRules.nextLevel(defaults, 1_000_000), 0);
        assertNull(BlindStructureRules.nextLevel(defaults, 2_000_000));
    }

    private static double[][] levels(double... values) {
        double[][] result = new double[values.length / 2][];
        for (int index = 0; index < result.length; index++) {
            result[index] = new double[]{values[index * 2],
                values[index * 2 + 1]};
        }
        return result;
    }
}
