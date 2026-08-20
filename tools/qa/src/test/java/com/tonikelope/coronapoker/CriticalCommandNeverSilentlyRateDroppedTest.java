package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalCommandNeverSilentlyRateDroppedTest {
    @Test
    public void rateLimitedGameCommandRequiresExplicitClose() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
        GameCommandGate.Decision decision = gate.rejectForRateLimit("ACTION");
        assertTrue(decision.closeConnection());
        assertFalse(decision.enqueue());
    }
}
