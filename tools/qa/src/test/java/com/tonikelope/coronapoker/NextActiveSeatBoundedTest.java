package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class NextActiveSeatBoundedTest {

    @Test
    public void impossibleSearchVisitsEverySeatAtMostOnce() {
        AtomicInteger visits = new AtomicInteger();

        assertFalse(SeatRing.nextActiveSeat(7, 19, seat -> {
            visits.incrementAndGet();
            return false;
        }).isPresent());
        assertEquals(7, visits.get());
    }
}
