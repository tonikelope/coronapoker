package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class GameFrameTeardownCancellationTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptedPresentationPauseCannotAbortCriticalRecoveryTeardown() {
        // Warm GameFrame/Helpers on a clean thread. Java 25's ImageIO initialization uses a
        // SecureRandom-backed temp name and correctly refuses to initialize while interrupted;
        // that is unrelated to the teardown cancellation contract under test.
        GameFrame.pauseBeforeRecoveryTeardown(0L);

        Thread.currentThread().interrupt();

        GameFrame.pauseBeforeRecoveryTeardown(5_000L);

        assertFalse(Thread.currentThread().isInterrupted(),
                "the critical teardown must continue on a clean interrupt state");
    }
}
