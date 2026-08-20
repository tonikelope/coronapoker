package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class RabbitWireWiringTest {
    @Test
    public void productionUsesOnlyRequestAndAuthorizationV1() throws Exception {
        Path root = locateRoot();
        String dealer = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String participant = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java"));
        String waiting = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java"));
        String card = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Card.java"));

        assertTrue(dealer.contains("RABBIT_REQ#"));
        assertTrue(dealer.contains("RABBIT_AUTH#"));
        assertTrue(participant.contains("case \"RABBIT_REQ\""));
        assertTrue(waiting.contains("case \"RABBIT_AUTH\""));
        assertTrue(card.contains(".REQUEST_RABBIT("));
        assertFalse(dealer.contains("RABBIT_HANDLER"));
        assertFalse(dealer.contains("\"RABBIT#\""));
        assertFalse(participant.contains("case \"RABBIT\""));
        assertFalse(waiting.contains("case \"RABBIT\""));
        assertFalse(card.contains("incrementContaRabbit"));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) return path;
        }
        throw new IllegalStateException("CoronaPoker root not found");
    }
}
