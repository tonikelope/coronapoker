package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class UnknownNamesCannotClearReplayStateTest {
    @Test
    public void arbitraryNamesCannotEvictKnownDedupEntry() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
        gate.accept("ACTION", 41);
        for (int i = 0; i < 10_000; i++) gate.accept("UNKNOWN_" + i, i);
        assertFalse(gate.accept("ACTION", 41).enqueue());
    }
}
