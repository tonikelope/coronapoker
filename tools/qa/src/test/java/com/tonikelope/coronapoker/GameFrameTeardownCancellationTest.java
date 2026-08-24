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
        Thread.currentThread().interrupt();

        GameFrame.pauseBeforeRecoveryTeardown(5_000L);

        assertFalse(Thread.currentThread().isInterrupted(),
                "the critical teardown must continue on a clean interrupt state");
    }
}
