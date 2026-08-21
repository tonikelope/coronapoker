package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShowdownEligibilityTest {

    @Test
    void disconnectedAllInRemainsInShowdownButOtherExitsDoNot() {
        assertFalse(Crupier.shouldRemoveExitedPlayerFromShowdown(true, Player.ALLIN));
        assertTrue(Crupier.shouldRemoveExitedPlayerFromShowdown(true, Player.FOLD));
        assertTrue(Crupier.shouldRemoveExitedPlayerFromShowdown(true, Player.BET));
        assertFalse(Crupier.shouldRemoveExitedPlayerFromShowdown(false, Player.BET));
    }

    @Test
    void disconnectedAllInCannotBeDroppedFromMandatoryPotCardsProofs() throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        String host = methodBody(source, "solicitarYRecibirCartasVisuales");
        String client = methodBody(source, "recibirCartasResistencia");

        assertTrue(host.contains("requiresShowdownProof("),
                "the host proof roster must use the same eligibility rule as settlement");
        assertFalse(host.contains("pendientes.removeIf("),
                "disconnecting while a mandatory reveal is pending cannot manufacture completion");
        assertTrue(client.contains("requiresShowdownProof("),
                "clients must verify the same all-in proof roster that the host settles");
    }

    private static String methodBody(String source, String methodName) {
        int name = source.indexOf(methodName + "(");
        int open = source.indexOf('{', name);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new IllegalArgumentException("method not found or unterminated: " + methodName);
    }

    private static Path sourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker source root not found");
    }
}
