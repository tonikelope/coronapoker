package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class GameConfigWireWiringTest {

    @Test
    public void initAndBlindUpdatesUseOnlyTheStrictV1Codec() throws IOException {
        Path root = locateRoot();
        String waiting = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java"));
        String dealer = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String settings = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/GameSettingsPanel.java"));

        assertTrue(waiting.contains("GameConfigWireV1.decodeBase64(partes_comando[3])"));
        assertTrue(waiting.contains("Invalid INIT configuration; closing connection"));
        assertTrue(waiting.contains("Invalid UPDATEBLINDS configuration; closing connection"));
        assertTrue(dealer.contains("\"INIT#\" + config.value().encodeBase64()"));
        assertTrue(settings.contains("\"UPDATEBLINDS#\" + encodedConfig"));
        assertFalse(dealer.contains("INIT#\" + String.valueOf(GameFrame.BUYIN)"));
        assertFalse(waiting.contains("GameFrame.BUYIN = Integer.parseInt(partes_comando[3])"));
        assertFalse(waiting.contains("GameFrame.BLIND_CAP = partes_comando.length > 7"));
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
