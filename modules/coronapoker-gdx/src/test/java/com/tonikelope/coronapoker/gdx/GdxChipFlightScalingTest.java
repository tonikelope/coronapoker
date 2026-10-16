package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
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
    void dynamicModeUsesFourStableDenominationColors() {
        for (double amount : new double[]{0.1d, 0.2d, 0.5d, 1d, 1.7d, 3d}) {
            for (long seed = 0; seed < 64; seed++) {
                for (CoronaPokerGdxTable.VisualChipSpec chip
                        : CoronaPokerGdxTable.dynamicVisualChips(
                                amount, 0.1d, seed)) {
                    int expectedColor = switch ((int) Math.round(
                            chip.amount() * 100d)) {
                        case 10 -> 1;
                        case 20 -> 3;
                        case 50 -> 0;
                        case 100 -> 2;
                        default -> throw new AssertionError(
                                "Unexpected denomination " + chip.amount());
                    };
                    assertEquals(expectedColor, chip.color());
                }
            }
        }
    }

    @Test
    void bigBlindMayBeOneBlueChipOrTwoSmallBlindChips() {
        Set<List<Double>> combinations = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            0.2d, 0.1d, seed);
            combinations.add(chips.stream()
                    .map(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sorted().toList());
            assertEquals(0.2d, chips.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
        }
        assertTrue(combinations.contains(List.of(0.2d)));
        assertTrue(combinations.contains(List.of(0.1d, 0.1d)));
    }

    @Test
    void staticModeReproducesTheMasterReleaseCountsExactly() {
        assertEquals(3, CoronaPokerGdxTable.actionVisualChipCount(
                TableVisualEvent.PlayerAction.ActionKind.CALL,
                100d, 0.1d, false));
        assertEquals(4, CoronaPokerGdxTable.actionVisualChipCount(
                TableVisualEvent.PlayerAction.ActionKind.RAISE,
                0.1d, 0.1d, false));
        assertEquals(7, CoronaPokerGdxTable.actionVisualChipCount(
                TableVisualEvent.PlayerAction.ActionKind.ALL_IN,
                1_000d, 0.1d, false));
        assertEquals(18, CoronaPokerGdxTable.payoutVisualChipCount(
                0.1d, 0.1d, false));
        assertEquals(14, CoronaPokerGdxTable.rebuyVisualChipCount(
                1_000d, 0.1d, false));
        assertEquals(14, CoronaPokerGdxTable.rebuyVisualChipCount(
                1_000d, 0.1d, true));
        assertEquals(1, CoronaPokerGdxTable.collectedVisualChipCount(
                TableSnapshot.Position.SMALL_BLIND, 100d, 0.1d, false));
        assertEquals(2, CoronaPokerGdxTable.collectedVisualChipCount(
                TableSnapshot.Position.BIG_BLIND, 0.1d, 0.1d, false));
        assertEquals(3, CoronaPokerGdxTable.collectedVisualChipCount(
                TableSnapshot.Position.DEALER_STRADDLE,
                1_000d, 0.1d, false));
    }

    @Test
    void dynamicCombinationsStayCompactExactAndDeterministic() {
        Set<List<Double>> combinations = new HashSet<>();
        for (long seed = 0; seed < 128; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            3d, 0.1d, seed);
            assertTrue(chips.size() >= 3);
            assertTrue(chips.size() <= 6);
            assertEquals(3d, chips.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
            assertTrue(chips.stream().allMatch(chip -> chip.amount() >= 0.5d),
                    "Randomisation must split the largest chips first");
            assertEquals(chips, CoronaPokerGdxTable.dynamicVisualChips(
                    3d, 0.1d, seed));
            combinations.add(chips.stream()
                    .map(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sorted().toList());
        }
        assertTrue(combinations.size() > 1);
    }

    @Test
    void eachOriginalChipSplitsAtMostOneGeneration() {
        Set<List<Double>> combinations = new HashSet<>();
        for (long seed = 0; seed < 512; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            1.3d, 0.1d, seed);
            List<Double> composition = chips.stream()
                    .map(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sorted().toList();
            combinations.add(composition);
            assertTrue(List.of(
                    List.of(0.1d, 0.2d, 1d),
                    List.of(0.1d, 0.2d, 0.5d, 0.5d),
                    List.of(0.1d, 0.1d, 0.1d, 1d),
                    List.of(0.1d, 0.1d, 0.1d, 0.5d, 0.5d))
                    .contains(composition),
                    () -> "Recursive or invalid split: " + composition);
            assertEquals(1.3d, chips.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
        }
        assertEquals(4, combinations.size(),
                "Both original chips must vary independently");
    }

    @Test
    void oneGenerationMayExceedDoubleButNeverTheTwentyChipVariationCap() {
        Set<Integer> halfUnitCounts = new HashSet<>();
        for (long seed = 0; seed < 128; seed++) {
            halfUnitCounts.add(CoronaPokerGdxTable.dynamicVisualChips(
                    0.5d, 0.1d, seed).size());
        }
        assertEquals(Set.of(1, 3), halfUnitCounts,
                "A x5 chip may become x2 + x2 + x1 in one generation");

        double[] amounts = {0.1d, 0.2d, 0.5d, 1d, 1.7d, 3d, 10d, 25d};
        int[] minimumCounts = {1, 1, 1, 1, 3, 3, 10, 25};
        for (int amountIndex = 0; amountIndex < amounts.length;
                amountIndex++) {
            double amount = amounts[amountIndex];
            int maximum = 0;
            for (long seed = 0; seed < 256; seed++) {
                int count = CoronaPokerGdxTable.dynamicVisualChips(
                        amount, 0.1d, seed).size();
                maximum = Math.max(maximum, count);
            }
            if (minimumCounts[amountIndex] <= 20) {
                assertTrue(maximum <= 20,
                        () -> "Unbounded visual chip count for amount "
                                + amount);
            } else {
                assertEquals(minimumCounts[amountIndex], maximum,
                        "Amounts whose minimum exceeds twenty must not vary");
            }
        }
    }

    @Test
    void dynamicModeCapsVariationAtTwentyButNeverTruncatesTheMinimum() {
        int twelveChipMaximum = Integer.MIN_VALUE;
        for (long seed = 0; seed < 128; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            12d, 0.1d, seed);
            twelveChipMaximum = Math.max(twelveChipMaximum, chips.size());
            assertTrue(chips.size() >= 12);
            assertTrue(chips.size() <= 20);
            assertEquals(12d, chips.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
        }
        assertTrue(twelveChipMaximum > 12,
                "A compact twelve-chip amount must still vary");

        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (long seed = 0; seed < 64; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            30d, 0.1d, seed);
            minimum = Math.min(minimum, chips.size());
            maximum = Math.max(maximum, chips.size());
            assertEquals(30, chips.size());
            assertEquals(30d, chips.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
        }
        assertEquals(30, minimum,
                "The compact thirty-chip composition must remain possible");
        assertEquals(30, maximum,
                "Variation must stop when the minimum already exceeds twenty");
    }

    @Test
    void dynamicModePreservesOddCentRemaindersWithoutExtraNoise() {
        List<CoronaPokerGdxTable.VisualChipSpec> chips
                = CoronaPokerGdxTable.dynamicVisualChips(
                        0.15d, 0.1d, 7L);
        assertEquals(1, chips.size());
        assertEquals(0.15d, chips.get(0).amount(), 0.000_001d);
        assertEquals(1, chips.get(0).color());
        assertTrue(CoronaPokerGdxTable.dynamicVisualChips(
                0d, 0.1d, 7L).isEmpty());
        assertTrue(CoronaPokerGdxTable.dynamicVisualChips(
                -1d, 0.1d, 7L).isEmpty());
        assertTrue(CoronaPokerGdxTable.dynamicVisualChips(
                Double.NaN, 0.1d, 7L).isEmpty());
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
