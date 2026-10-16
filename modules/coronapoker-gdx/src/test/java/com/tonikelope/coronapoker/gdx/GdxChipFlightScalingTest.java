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
    void paymentsUseFiveStableDenominationColors() {
        for (double amount : new double[]{0.1d, 0.2d, 0.5d, 1d,
            1.7d, 3d}) {
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
                        case 250 -> 4;
                        default -> throw new AssertionError(
                                "Unexpected denomination " + chip.amount());
                    };
                    assertEquals(expectedColor, chip.color());
                }
            }
        }
    }

    @Test
    void bigBlindAlwaysUsesOneGoldChip() {
        for (long seed = 0; seed < 64; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            0.2d, 0.1d, seed);
            assertEquals(List.of(new CoronaPokerGdxTable.VisualChipSpec(
                    0.2d, 3)), chips);
        }
    }

    @Test
    void paymentCompositionIsMinimumExactAndIndependentOfSeed() {
        double[] amounts = {0.1d, 0.2d, 0.5d, 1d, 1.7d, 3d, 10d, 25d};
        int[] minimumCounts = {1, 1, 1, 1, 3, 3, 10, 25};
        for (int amountIndex = 0; amountIndex < amounts.length;
                amountIndex++) {
            List<CoronaPokerGdxTable.VisualChipSpec> baseline
                    = sortedComposition(amounts[amountIndex], 0L);
            assertEquals(minimumCounts[amountIndex], baseline.size());
            assertEquals(amounts[amountIndex], baseline.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
            for (long seed = 1; seed < 64; seed++) {
                assertEquals(baseline,
                        sortedComposition(amounts[amountIndex], seed));
            }
        }
    }

    @Test
    void onlyTheFlightOrderVaries() {
        Set<List<CoronaPokerGdxTable.VisualChipSpec>> launchOrders
                = new HashSet<>();
        List<CoronaPokerGdxTable.VisualChipSpec> expected = List.of(
                new CoronaPokerGdxTable.VisualChipSpec(0.1d, 1),
                new CoronaPokerGdxTable.VisualChipSpec(0.2d, 3),
                new CoronaPokerGdxTable.VisualChipSpec(0.5d, 0),
                new CoronaPokerGdxTable.VisualChipSpec(1d, 2));
        for (long seed = 0; seed < 64; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.dynamicVisualChips(
                            1.8d, 0.1d, seed);
            assertEquals(expected, chips.stream()
                    .sorted((left, right) -> Double.compare(
                            left.amount(), right.amount())).toList());
            launchOrders.add(chips);
        }
        assertTrue(launchOrders.size() > 1);
    }

    @Test
    void oddCentRemaindersRemainExactWithoutAnExtraChip() {
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
    void rebuyPackKeepsAllFourFamiliesAndOnlyVariesItsOrder() {
        Set<List<CoronaPokerGdxTable.VisualChipSpec>> orders = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) {
            List<CoronaPokerGdxTable.VisualChipSpec> chips
                    = CoronaPokerGdxTable.rebuyVisualChips(
                            10d, 0.1d, seed);
            assertEquals(21, chips.size());
            assertEquals(Set.of(0, 1, 2, 3), chips.stream()
                    .map(CoronaPokerGdxTable.VisualChipSpec::color)
                    .collect(java.util.stream.Collectors.toSet()));
            assertEquals(10d, chips.stream()
                    .mapToDouble(CoronaPokerGdxTable.VisualChipSpec::amount)
                    .sum(), 0.000_001d);
            orders.add(chips);
        }
        assertTrue(orders.size() > 1);
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

    private static List<CoronaPokerGdxTable.VisualChipSpec> sortedComposition(
            double amount, long seed) {
        return CoronaPokerGdxTable.dynamicVisualChips(amount, 0.1d, seed)
                .stream().sorted((left, right) -> {
                    int byAmount = Double.compare(left.amount(), right.amount());
                    return byAmount != 0 ? byAmount
                            : Integer.compare(left.color(), right.color());
                }).toList();
    }
}
