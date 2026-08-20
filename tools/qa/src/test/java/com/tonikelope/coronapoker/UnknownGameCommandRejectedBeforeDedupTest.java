package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class UnknownGameCommandRejectedBeforeDedupTest {
    @Test
    public void unknownNameNeverTouchesReplayMemory() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
        gate.accept("ACTION", 1, "GAME#1#ACTION#a");
        int before = gate.dedupSize();
        gate.accept("UNKNOWN", 2, "GAME#2#UNKNOWN");
        assertEquals(before, gate.dedupSize());
    }
}
