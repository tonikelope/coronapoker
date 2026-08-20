package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class GameCommandNonAdjacentReplayRejectedTest {

    @Test
    public void replayRemainsRejectedAfterAnInterveningCommand() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);

        assertTrue(gate.accept("ACTION", 1, "GAME#1#ACTION#a").enqueue());
        assertTrue(gate.accept("ACTION", 2, "GAME#2#ACTION#b").enqueue());

        GameCommandGate.Decision replay = gate.accept(
                "ACTION", 1, "GAME#1#ACTION#a");
        assertTrue(replay.acknowledge());
        assertFalse(replay.enqueue());
        assertFalse(replay.closeConnection());
    }
}
