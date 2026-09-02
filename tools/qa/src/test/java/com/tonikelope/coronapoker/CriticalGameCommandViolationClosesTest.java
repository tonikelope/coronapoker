package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.network.GameCommandGate;
import com.tonikelope.coronapoker.core.network.GameCommandType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalGameCommandViolationClosesTest {

    @Test
    public void malformedOrSpoofedCriticalFrameClosesWithoutAckOrEnqueue() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);

        GameCommandGate.Decision decision = gate.rejectCriticalViolation();

        assertFalse(decision.acknowledge());
        assertFalse(decision.enqueue());
        assertTrue(decision.closeConnection());
    }
}
