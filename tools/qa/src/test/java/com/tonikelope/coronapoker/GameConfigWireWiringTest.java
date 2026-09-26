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
        String gateway = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/core/network/NetworkLobbyGateway.java"));
        String dealer = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        assertTrue(gateway.contains("GameConfigCodecV1.decodeBase64(parts[3])"));
        assertTrue(gateway.contains("Invalid INIT configuration:"));
        assertTrue(dealer.contains("GameConfigCodecV1.decodeBase64(parts[1])"));
        assertTrue(dealer.contains("failClientCriticalHostCommand(\"UPDATEBLINDS\", failure)"));
        assertTrue(dealer.contains("GameConfigCodecV1.encodeBase64(config)"));
        assertTrue(dealer.contains("gameSession().updateConfiguration(config)"));
        assertTrue(dealer.contains("\"UPDATEBLINDS#\""));
        assertFalse(dealer.contains("INIT#\" + String.valueOf(GameFrame.BUYIN)"));
        assertFalse(gateway.contains("GameFrame.BUYIN"));
        assertFalse(gateway.contains("GameFrame.BLIND_CAP"));
    }

    @Test
    public void hostInstallsValidatedConfigurationBeforeFirstSharedRead() throws IOException {
        String dealer = Files.readString(locateRoot().resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int run = dealer.indexOf("public void run()");
        int install = dealer.indexOf("gameSession().updateConfiguration(config)", run);
        int blinds = dealer.indexOf(
                "this.ciega_pequeña = configuration().smallBlind()", install);
        int progress = dealer.indexOf(
                "game_progress.reset(configuration().thinkTime())", blinds);

        assertTrue(run >= 0 && run < install);
        assertTrue(install < blinds && blinds < progress,
                "host must install strict INIT before shared configuration reads");
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
