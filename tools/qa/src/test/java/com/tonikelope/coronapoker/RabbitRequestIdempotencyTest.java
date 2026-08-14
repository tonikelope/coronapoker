package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * TDD guard for the money-bearing RABBIT protocol. The same counter may be
 * delivered more than once by a retrying/replaying peer; only a new positive
 * counter is allowed to reach the fee/stack mutation.
 */
class RabbitRequestIdempotencyTest {

    @Test
    void eachPositiveRabbitCounterIsAcceptedOnceEvenWhenWorkersReorder() {
        Set<Integer> applied = new HashSet<>();

        assertTrue(Crupier.shouldAcceptRabbitCount(1, applied));
        applied.add(1);
        assertTrue(Crupier.shouldAcceptRabbitCount(3, applied));
        applied.add(3);
        assertTrue(Crupier.shouldAcceptRabbitCount(2, applied),
                "an older arrival is still a distinct fee in mode 3");
        applied.add(2);
        assertFalse(Crupier.shouldAcceptRabbitCount(2, applied),
                "a retransmitted counter must not charge the fee twice");
        assertFalse(Crupier.shouldAcceptRabbitCount(0, applied));
        assertFalse(Crupier.shouldAcceptRabbitCount(-1, applied));
    }
}
