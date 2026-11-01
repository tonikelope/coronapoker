package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImmediateRebuyExposureTest {

    @Test
    void includesEveryChipAlreadyInvestedInTheCurrentHand() {
        assertEquals(10d, Crupier.rebuyTableExposure(7.9d, 2.1d));
        assertEquals(10d, Crupier.rebuyTableExposure(0d, 10d));
        assertEquals(9.9d, Crupier.rebuyTableExposure(9.8d, 0.1d));
    }

    @Test
    void neverCreatesNegativeExposureFromInvalidComponents() {
        assertEquals(4d, Crupier.rebuyTableExposure(-2d, 4d));
        assertEquals(3d, Crupier.rebuyTableExposure(3d, -1d));
        assertEquals(0d, Crupier.rebuyTableExposure(-2d, -1d));
    }

    @Test
    void globalBotRebuyIsTheMasterForStandardAndCustomBots() {
        assertFalse(Crupier.botAutomaticRebuyAllowed(true, false, true,
                true));
        assertFalse(Crupier.botAutomaticRebuyAllowed(true, false, false,
                true));
        assertTrue(Crupier.botAutomaticRebuyAllowed(true, true, true,
                false));
        assertTrue(Crupier.botAutomaticRebuyAllowed(true, true, false,
                true));
        assertFalse(Crupier.botAutomaticRebuyAllowed(true, true, false,
                false));
    }

    @Test
    void humanRebuyDoesNotDependOnBotSettings() {
        assertTrue(Crupier.botAutomaticRebuyAllowed(false, false, false,
                false));
    }

    @Test
    void onlyTheHostCanManuallyReturnAnEligibleBotSpectator() {
        assertTrue(Crupier.botManualRebuyAllowed(true, true, true, false,
                false));
        assertFalse(Crupier.botManualRebuyAllowed(false, true, true, false,
                false));
        assertFalse(Crupier.botManualRebuyAllowed(true, false, true, false,
                false));
        assertFalse(Crupier.botManualRebuyAllowed(true, true, false, false,
                false));
        assertFalse(Crupier.botManualRebuyAllowed(true, true, true, true,
                false));
        assertFalse(Crupier.botManualRebuyAllowed(true, true, true, false,
                true));
    }
}
