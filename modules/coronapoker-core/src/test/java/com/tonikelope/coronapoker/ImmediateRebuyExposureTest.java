package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
