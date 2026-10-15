package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxChipFlightScalingTest {

    @Test
    void visualChipCountGrowsLogarithmicallyWithRealChipUnits() {
        assertEquals(1, CoronaPokerGdxTable.visualChipCount(0.1d, 0.1d));
        assertEquals(2, CoronaPokerGdxTable.visualChipCount(0.2d, 0.1d));
        assertEquals(4, CoronaPokerGdxTable.visualChipCount(0.5d, 0.1d));
        assertEquals(6, CoronaPokerGdxTable.visualChipCount(1d, 0.1d));
        assertEquals(7, CoronaPokerGdxTable.visualChipCount(2d, 0.1d));
        assertEquals(9, CoronaPokerGdxTable.visualChipCount(5d, 0.1d));
        assertEquals(10, CoronaPokerGdxTable.visualChipCount(10d, 0.1d));
        assertEquals(14, CoronaPokerGdxTable.visualChipCount(50d, 0.1d));
        assertEquals(15, CoronaPokerGdxTable.visualChipCount(100d, 0.1d));
        assertEquals(20, CoronaPokerGdxTable.visualChipCount(1_000d, 0.1d));
    }

    @Test
    void visualChipCountNeverInventsMoreWholeChipsThanTheAmountContains() {
        assertEquals(1, CoronaPokerGdxTable.visualChipCount(0.15d, 0.1d));
        assertEquals(0, CoronaPokerGdxTable.visualChipCount(0d, 0.1d));
        assertEquals(0, CoronaPokerGdxTable.visualChipCount(-1d, 0.1d));
        assertEquals(0, CoronaPokerGdxTable.visualChipCount(
                Double.NaN, 0.1d));
    }

    @Test
    void landingSoundsScaleWithTheVisualChipCount() {
        assertEquals(0, CoronaPokerGdxTable.landingImpactCount(0));
        assertEquals(1, CoronaPokerGdxTable.landingImpactCount(1));
        assertEquals(2, CoronaPokerGdxTable.landingImpactCount(5));
        assertEquals(3, CoronaPokerGdxTable.landingImpactCount(8));
        assertEquals(6, CoronaPokerGdxTable.landingImpactCount(18));
        assertEquals(12, CoronaPokerGdxTable.landingImpactCount(36));
    }

    @Test
    void equalAmountsAlwaysProduceTheSameCountAndPreserveTheirValue() {
        int count = CoronaPokerGdxTable.visualChipCount(2d, 0.1d);
        assertEquals(7, count);
        double total = 0d;
        for (double part : CoronaPokerGdxTable.splitVisualCounterAmounts(
                2d, count, 0.1d)) {
            total += part;
        }
        assertEquals(2d, total, 0.000_001d);
    }

    @Test
    void impactVariationIsSubtleDeterministicAndSafelyBounded() {
        Set<Float> volumes = new HashSet<>();
        Set<Float> pitches = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) {
            float volume = CoronaPokerGdxTable.variedChipImpactVolume(
                    0.24f, seed);
            float pitch = CoronaPokerGdxTable.variedChipImpactPitch(1f, seed);
            volumes.add(volume);
            pitches.add(pitch);
            assertTrue(volume >= 0.223f && volume <= 0.257f);
            assertTrue(pitch >= 0.985f && pitch <= 1.015f);
            assertEquals(volume, CoronaPokerGdxTable.variedChipImpactVolume(
                    0.24f, seed));
            assertEquals(pitch, CoronaPokerGdxTable.variedChipImpactPitch(
                    1f, seed));
        }
        assertTrue(volumes.size() > 1);
        assertTrue(pitches.size() > 1);
    }

    @Test
    void visualChipColorsRemainWithinThePackagedChipSet() {
        Set<Integer> colors = new HashSet<>();
        for (int index = 0; index < 18; index++) {
            int color = CoronaPokerGdxTable.variedVisualChipColor(
                    1, 42L, index);
            colors.add(color);
            assertTrue(color >= 0 && color < 4);
        }
        assertTrue(colors.size() > 1);
    }

    @Test
    void eighteenChipsPreserveTheExistingSixImpactCadence() {
        List<Integer> soundingRanks = new ArrayList<>();
        for (int rank = 0; rank < 18; rank++) {
            if (CoronaPokerGdxTable.landingImpactAtRank(rank, 18)) {
                soundingRanks.add(rank);
            }
        }
        assertEquals(List.of(0, 3, 6, 9, 12, 15), soundingRanks);
        assertFalse(CoronaPokerGdxTable.landingImpactAtRank(-1, 18));
        assertFalse(CoronaPokerGdxTable.landingImpactAtRank(18, 18));
        assertTrue(CoronaPokerGdxTable.landingImpactAtRank(0, 1));
    }
}
