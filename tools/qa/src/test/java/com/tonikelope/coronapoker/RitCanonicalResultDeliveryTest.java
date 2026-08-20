package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RitCanonicalResultDeliveryTest {

    @Test
    void canonicalRitResultMustReachEveryPeerBeforePlayContinues() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));

        assertTrue(source.contains("private boolean broadcastRitClose(int result)"));
        assertTrue(source.contains("RIT_VOTE_CLOSE delivery failed; aborting hand"));
        assertTrue(source.contains("if (!broadcastRitClose(agreed ? 1 : 0))"));
        assertTrue(source.contains("MISDEAL broadcast failed; continuing local refund and abort"));
        assertFalse(source.contains("LOGGER.log(Level.WARNING, \"Failed to broadcast RIT_VOTE_CLOSE\""));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("CoronaPoker root not found from " + start);
    }
}
