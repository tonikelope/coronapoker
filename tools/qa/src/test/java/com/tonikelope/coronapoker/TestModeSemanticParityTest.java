package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestModeSemanticParityTest {

    @Test
    void everyProductionTestModeShortcutBelongsToTheReviewedInventory()
            throws Exception {
        Map<String, Integer> reviewedOccurrences = Map.of(
                "Audio.java", 8,
                "Crupier.java", 12,
                "GameFrame.java", 5,
                "Init.java", 1,
                "LocalPlayer.java", 3,
                "RemotePlayer.java", 1);

        try (var sources = Files.list(sourceRoot())) {
            Map<String, Integer> actual = sources
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> read(path).contains("TEST_MODE"))
                    .collect(java.util.stream.Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> occurrences(read(path), "TEST_MODE")));
            org.junit.jupiter.api.Assertions.assertEquals(reviewedOccurrences, actual,
                    "A TEST_MODE shortcut was added, removed or moved; review its semantic "
                    + "effect before updating this inventory");
        }
    }

    @Test
    void acceleratedHandCloseKeepsPlayerAndRabbitStateTransitions() throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        int accelerated = source.indexOf(
                "this.pausaConBarra(Crupier.PAUSA_ENTRE_MANOS_TEST)");
        int branchEnd = source.indexOf(
                "// (IWTSTH/RIT/Rabbit rules are no longer re-enabled here", accelerated);
        assertTrue(accelerated >= 0 && branchEnd > accelerated);

        String body = source.substring(accelerated, branchEnd);
        assertTrue(body.contains("checkRebuyTime()"));
        assertTrue(body.contains("exitSpectatorBots()"));
        assertTrue(body.contains("updateExitPlayers()"));
        assertTrue(body.contains("waitRabbitProcessing()"));
    }

    @Test
    void testModeRebuyUsesNormalNextHandAccountingInsteadOfFakeActiveSeats()
            throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        int method = source.indexOf("public void checkRebuyTime()");
        int normalPath = source.indexOf(
                "ArrayList<String> rebuy_players = new ArrayList<>()", method);
        assertTrue(method >= 0 && normalPath > method);

        String testPath = source.substring(method, normalPath);
        assertTrue(testPath.contains("if (GameFrame.TEST_MODE)"));
        assertTrue(testPath.contains("rebuy_now.put(jugador.getNickname(), amount)"));
        assertTrue(testPath.contains("jugador.setSpectator(null)"));
        assertFalse(testPath.contains("setStack("));
        assertFalse(testPath.contains("pagar("));
    }

    private static Path sourceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate CoronaPoker production sources");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }
}
