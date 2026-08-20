package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class RecoveryZeroActivePlayersRejectedTest {

    @Test
    public void zeroActiveRecoveryRingHasNoSeat() {
        assertFalse(SeatRing.nextActiveSeat(4, 0, seat -> false).isPresent());
    }
}
