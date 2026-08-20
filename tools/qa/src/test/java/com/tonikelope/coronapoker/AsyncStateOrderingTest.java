package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Pure TDD coverage for state changes dispatched from a socket reader to the
 * cached pool. REBUYNOW/REBUYDENIED and PAUSE share this gate; the production
 * handlers still remain asynchronous because the reader must be able to consume
 * confirmation frames.
 */
class AsyncStateOrderingTest {

    @Test
    void onlyTheNewestArrivalMayCommitAfterPoolReordering() {
        assertTrue(Crupier.shouldApplyAsyncSequence(1L, 0L));
        assertTrue(Crupier.shouldApplyAsyncSequence(2L, 1L));
        assertFalse(Crupier.shouldApplyAsyncSequence(2L, 2L),
                "a duplicate async task must be idempotent");
        assertFalse(Crupier.shouldApplyAsyncSequence(1L, 2L),
                "a late task must not roll back the newest toggle");
    }

    @Test
    void unsequencedCallsAreRejected() {
        assertFalse(Crupier.shouldApplyAsyncSequence(0L, 99L),
                "state-changing calls must carry a current positive arrival sequence");
    }
}
