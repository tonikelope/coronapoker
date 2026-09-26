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
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String factory = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/CoreGameTableFactory.java"));

        assertTrue(dealer.contains("RABBIT_REQ#"));
        assertTrue(dealer.contains("RABBIT_AUTH#"));
        assertTrue(factory.contains("command.command().startsWith(\"RABBIT_REQ#\")"));
        assertTrue(factory.contains("command.command().startsWith(\"RABBIT_AUTH#\")"));
        assertTrue(factory.contains("dealer.REQUEST_RABBIT(local.getNickname())"));
        assertFalse(dealer.contains("RABBIT_HANDLER"));
        assertFalse(dealer.contains("\"RABBIT#\""));
        assertFalse(factory.contains("incrementContaRabbit"));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) return path;
        }
        throw new IllegalStateException("CoronaPoker root not found");
    }
}
