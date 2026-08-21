package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class PauseWireTest {

    @Test
    void clientRequestHasOneCurrentExactShape() {
        assertTrue(PauseWire.parseClientRequest(new String[]{"GAME", "1", "PAUSE", "1"}));
        assertFalse(PauseWire.parseClientRequest(new String[]{"GAME", "2", "PAUSE", "0"}));
        assertThrows(IllegalArgumentException.class, () -> PauseWire.parseClientRequest(
                new String[]{"GAME", "1", "PAUSE", "1", ""}));
        assertThrows(IllegalArgumentException.class, () -> PauseWire.parseClientRequest(
                new String[]{"GAME", "1", "PAUSE", "2"}));
    }

    @Test
    void hostRelayRequiresTheCurrentOwnerField() {
        String owner = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        PauseWire.Relay relay = PauseWire.parseHostRelay(
                new String[]{"GAME", "3", "PAUSE", "1", owner});
        assertTrue(relay.paused());
        assertEquals("alice", relay.owner());

        assertThrows(IllegalArgumentException.class, () -> PauseWire.parseHostRelay(
                new String[]{"GAME", "3", "PAUSE", "1"}));
        assertThrows(IllegalArgumentException.class, () -> PauseWire.parseHostRelay(
                new String[]{"GAME", "3", "PAUSE", "1", owner, ""}));
        assertThrows(IllegalArgumentException.class, () -> PauseWire.parseHostRelay(
                new String[]{"GAME", "3", "PAUSE", "1", "YQ"}));
    }

    @Test
    void invalidPauseClosesInsteadOfBeingAcknowledgedAndDropped() throws Exception {
        Path root = locateRoot();
        String participant = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java")).replace("\r\n", "\n");
        int hostCase = participant.indexOf("case \"PAUSE\":");
        int hostEnd = participant.indexOf("case \"IWTSTH\":", hostCase);
        String host = participant.substring(hostCase, hostEnd);
        assertTrue(host.contains("PauseWire.parseClientRequest(partes_comando)"));
        assertTrue(host.contains("exitAndCloseSocket()"));

        String waiting = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java")).replace("\r\n", "\n");
        int clientCase = waiting.indexOf("case \"PAUSE\":", waiting.indexOf("if (isPartida_empezada())"));
        int clientEnd = waiting.indexOf("case \"SHUFFLE_TURN\":", clientCase);
        String client = waiting.substring(clientCase, clientEnd);
        assertTrue(client.contains("PauseWire.parseHostRelay(partes_comando)"));
        assertTrue(client.contains("closeCriticalHostChannel()"));
        assertFalse(client.contains("partes_comando.length >= 5"), "legacy owner fallback remains");
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
                return path;
            }
        }
        throw new IllegalStateException("repository root not found");
    }
}
