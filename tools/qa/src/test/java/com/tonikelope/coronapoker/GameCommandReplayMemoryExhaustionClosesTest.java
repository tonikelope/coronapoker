package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class GameCommandReplayMemoryExhaustionClosesTest {

    @Test
    public void capacityExhaustionClosesInsteadOfEvictingOrDropping() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST, 2);
        assertTrue(gate.accept("ACTION", 1).enqueue());
        assertTrue(gate.accept("ACTION", 2).enqueue());

        GameCommandGate.Decision exhausted = gate.accept("ACTION", 3);
        assertFalse(exhausted.acknowledge());
        assertFalse(exhausted.enqueue());
        assertTrue(exhausted.closeConnection());

        GameCommandGate.Decision knownReplay = gate.accept("ACTION", 1);
        assertTrue(knownReplay.acknowledge());
        assertFalse(knownReplay.enqueue());
        assertFalse(knownReplay.closeConnection());
    }
}
