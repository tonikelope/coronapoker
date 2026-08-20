package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class UnknownNamesCannotClearReplayStateTest {
    @Test
    public void arbitraryNamesCannotEvictKnownDedupEntry() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
        gate.accept("ACTION", 41, "GAME#41#ACTION#a");
        for (int i = 0; i < 10_000; i++) {
            gate.accept("UNKNOWN_" + i, i, "GAME#" + i + "#UNKNOWN_" + i);
        }
        assertFalse(gate.accept("ACTION", 41, "GAME#41#ACTION#a").enqueue());
    }
}
