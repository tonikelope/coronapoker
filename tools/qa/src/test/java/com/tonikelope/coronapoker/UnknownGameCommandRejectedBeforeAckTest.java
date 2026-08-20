package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class UnknownGameCommandRejectedBeforeAckTest {
    @Test
    public void unknownNameClosesWithoutAck() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
        GameCommandGate.Decision decision = gate.accept("ATTACK_" + System.nanoTime(), 7);
        assertTrue(decision.closeConnection());
        assertFalse(decision.acknowledge());
    }
}
