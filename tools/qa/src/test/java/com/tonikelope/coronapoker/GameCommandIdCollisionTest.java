package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class GameCommandIdCollisionTest {

    @Test
    public void sameIdWithDifferentAuthenticatedBytesIsNeverSilentlyDroppedAsReplay() {
        GameCommandGate gate = new GameCommandGate(
                GameCommandType.Direction.CLIENT_TO_HOST);

        assertTrue(gate.accept("ACTION", 7, "GAME#7#ACTION#first").enqueue());
        GameCommandGate.Decision collision = gate.accept(
                "ACTION", 7, "GAME#7#ACTION#different");

        assertFalse(collision.acknowledge());
        assertFalse(collision.enqueue());
        assertTrue(collision.closeConnection());
    }

    @Test
    public void exactAuthenticatedRetransmissionRemainsIdempotent() {
        GameCommandGate gate = new GameCommandGate(
                GameCommandType.Direction.CLIENT_TO_HOST);
        String frame = "GAME#9#ACTION#same";

        assertTrue(gate.accept("ACTION", 9, frame).enqueue());
        GameCommandGate.Decision duplicate = gate.accept("ACTION", 9, frame);

        assertTrue(duplicate.acknowledge());
        assertFalse(duplicate.enqueue());
        assertFalse(duplicate.closeConnection());
    }
}
