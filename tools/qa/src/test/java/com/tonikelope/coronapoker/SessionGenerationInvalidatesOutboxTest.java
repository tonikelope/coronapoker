package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class SessionGenerationInvalidatesOutboxTest {
    @Test
    public void reconnectInvalidatesTheOldLeaseButPreservesPendingCriticalCommands() {
        SessionOutbox outbox = new SessionOutbox(8, 1024);
        assertTrue(outbox.offer("GAMEINFO#old"));
        assertTrue(outbox.offer("GAMECONFIG#current"));
        SessionOutbox.Entry old = outbox.peek();
        int wireId = old.wireId();

        outbox.advanceGenerationPreservingEntries();

        assertFalse(outbox.isCurrent(old));
        assertEquals(2, outbox.size());
        assertEquals("GAMEINFO#old", outbox.peek().command());
        assertEquals(wireId, outbox.peek().wireId());
        assertTrue(outbox.isCurrent(outbox.peek()));
        assertTrue(outbox.removeIfHead(outbox.peek()));
        assertEquals("GAMECONFIG#current", outbox.peek().command());
    }

    @Test
    public void participantReconnectUsesThePreservingGenerationTransition() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("src/main/java/com/tonikelope/coronapoker/Participant.java"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "repository root not found");
        String participant = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java"));
        assertTrue(participant.contains(
                "pre_game_socket_writer_queue.advanceGenerationPreservingEntries();"));
    }
}
