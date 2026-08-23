package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ClientMisdealTerminationTest {

    @Test
    void misdealTeardownBelongsToTheAuthoritativeTerminationFlow() {
        assertTrue(Crupier.shouldDeferMisdealTeardown(true));
        assertFalse(Crupier.shouldDeferMisdealTeardown(false));
    }
}
