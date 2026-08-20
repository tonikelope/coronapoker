package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class Rit2CommunityCommandsRegisteredTest {

    @Test
    void currentProtocolAdmitsEveryHostRit2CommunityPiece() {
        for (String command : new String[]{
            "RIT2_FLOP_PIECE", "RIT2_TURN_PIECE", "RIT2_RIVER_PIECE"
        }) {
            GameCommandType admitted = GameCommandType.from(
                    GameCommandType.Direction.HOST_TO_CLIENT, command);
            assertNotNull(admitted, command + " must be admitted from the host");
            assertEquals(command, admitted.name());
            assertNull(GameCommandType.from(GameCommandType.Direction.CLIENT_TO_HOST, command));
        }
    }
}
