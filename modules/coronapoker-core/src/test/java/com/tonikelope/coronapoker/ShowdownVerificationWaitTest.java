package com.tonikelope.coronapoker;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShowdownVerificationWaitTest {
    @Test
    void earlyVerdictPreventsTheWaitingModal() {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var waiting = new ShowdownVerificationWait(() -> {
            opens.incrementAndGet();
            return closes::incrementAndGet;
        });
        waiting.close();
        waiting.countdownExpired();
        assertEquals(0, opens.get());
        assertEquals(0, closes.get());
    }

    @Test
    void expiredCountdownOpensOnceAndVerdictOrCancellationClosesOnce() {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var waiting = new ShowdownVerificationWait(() -> {
            opens.incrementAndGet();
            return closes::incrementAndGet;
        });
        assertEquals(0, opens.get());
        waiting.countdownExpired();
        waiting.countdownExpired();
        assertEquals(1, opens.get());
        waiting.close();
        waiting.close();
        waiting.countdownExpired();
        assertEquals(1, closes.get());
        assertEquals(1, opens.get());
    }
}
