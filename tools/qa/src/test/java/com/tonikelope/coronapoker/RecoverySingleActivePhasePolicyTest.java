package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class RecoverySingleActivePhasePolicyTest {

    @Test
    public void openHandMayPreserveItsOnlyRemainingActiveSeat() {
        assertEquals(2, SeatRing.nextActiveSeat(4, 0, seat -> seat == 2).orElse(-1));
    }
}
