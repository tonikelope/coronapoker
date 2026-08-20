package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class CriticalCommandNeverSilentlyRateDroppedTest {
    @Test
    public void rateLimitedGameCommandRequiresExplicitClose() {
        GameCommandGate gate = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
        GameCommandGate.Decision decision = gate.rejectForRateLimit("ACTION");
        assertTrue(decision.closeConnection());
        assertFalse(decision.enqueue());
    }

    @Test
    public void publicSecurityContractRequiresClosingRateLimitedGameFrames() throws IOException {
        String security = Files.readString(projectRoot().resolve("docs/SECURITY.md"));
        assertTrue(security.contains(
                "Every `GAME` frame is critical: if it exceeds the size or rate budget, "
                + "the authenticated connection is closed explicitly"));
        assertFalse(security.contains(
                "Every inbound text command passes a per-peer **size cap** and **token bucket**"));
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("docs"))
                    && Files.isDirectory(current.resolve("src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker project root not found");
    }
}
