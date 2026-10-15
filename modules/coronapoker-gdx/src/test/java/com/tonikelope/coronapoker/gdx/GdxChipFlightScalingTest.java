package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxChipFlightScalingTest {

    @Test
    void visualChipCountGrowsLogarithmicallyWithRealChipUnits() {
        assertEquals(1, CoronaPokerGdxTable.visualChipCount(0.1d, 0.1d));
        assertEquals(2, CoronaPokerGdxTable.visualChipCount(0.2d, 0.1d));
        assertEquals(5, CoronaPokerGdxTable.visualChipCount(0.5d, 0.1d));
        assertEquals(8, CoronaPokerGdxTable.visualChipCount(1d, 0.1d));
        assertEquals(10, CoronaPokerGdxTable.visualChipCount(2d, 0.1d));
        assertEquals(12, CoronaPokerGdxTable.visualChipCount(5d, 0.1d));
        assertEquals(14, CoronaPokerGdxTable.visualChipCount(10d, 0.1d));
        assertEquals(18, CoronaPokerGdxTable.visualChipCount(50d, 0.1d));
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
    void landingSoundsScaleWithTheVisualChipCountAndRemainBounded() {
        assertEquals(0, CoronaPokerGdxTable.landingImpactCount(0));
        assertEquals(1, CoronaPokerGdxTable.landingImpactCount(1));
        assertEquals(2, CoronaPokerGdxTable.landingImpactCount(5));
        assertEquals(3, CoronaPokerGdxTable.landingImpactCount(8));
        assertEquals(6, CoronaPokerGdxTable.landingImpactCount(18));
        assertEquals(6, CoronaPokerGdxTable.landingImpactCount(36));
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
