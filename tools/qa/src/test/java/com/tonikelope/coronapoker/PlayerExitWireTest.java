package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class PlayerExitWireTest {

    @Test
    public void clientRequestIsBoundToItsAuthenticatedNick() {
        byte[] scalar = scalarOne();
        String testament = Base64.getEncoder().encodeToString(scalar);
        PlayerExitWire.Command parsed = PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "4", "EXIT", testament}, "alice");
        assertEquals("alice", parsed.nick());
        assertTrue(parsed.hasTestament());
        assertArrayEquals(scalar, parsed.testament());

        PlayerExitWire.Command absent = PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "5", "EXIT", "*"}, "alice");
        assertFalse(absent.hasTestament());
    }

    @Test
    public void hostRelayUsesTheSingleCurrentNickAndOptionalTestamentShape() {
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        PlayerExitWire.Command absent = PlayerExitWire.parseHostRelay(
                new String[]{"GAME", "6", "EXIT", nick});
        assertEquals("alice", absent.nick());
        assertFalse(absent.hasTestament());

        byte[] scalar = scalarOne();
        PlayerExitWire.Command present = PlayerExitWire.parseHostRelay(
                new String[]{"GAME", "7", "EXIT", nick, Base64.getEncoder().encodeToString(scalar)});
        assertArrayEquals(scalar, present.testament());
    }

    @Test
    public void malformedOrCompatibilityFormsAreRejected() {
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        String zeros = Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "4", "EXIT"}, "alice"));
        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "4", "EXIT", "*", "ignored"}, "alice"));
        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseClientRequest(
                new String[]{"GAME", "4", "EXIT", zeros}, "alice"));
        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseHostRelay(
                new String[]{"GAME", "6", "EXIT", nick, "*"}));
        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseHostRelay(
                new String[]{"GAME", "6", "EXIT", "%%%"}));
        assertThrows(IllegalArgumentException.class, () -> PlayerExitWire.parseHostRelay(
                new String[]{"GAME", "6", "EXIT", nick, zeros}));
    }

    private static byte[] scalarOne() {
        byte[] scalar = new byte[32];
        scalar[0] = 1;
        return scalar;
    }
}
