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
                "Crupier.java", 23,
                "GameFrame.java", 6,
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
    void protocolTestModeMarkersAreObservabilityOnly() throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        assertObservabilityOnly(source, "QA EXIT_TESTAMENT_ACCEPTED");
        assertObservabilityOnly(source, "QA RIT_VOTE_ACCEPTED");
        assertObservabilityOnly(source, "QA STRADDLE_RESP_ACCEPTED");
    }

    @Test
    void teardownTestModeMarkersAreObservabilityOnly() throws Exception {
        String source = Files.readString(sourceRoot().resolve("GameFrame.java"))
                .replace("\r\n", "\n");
        String reviewedMethod
                = "    private static void qaTeardownStage(String stage) {\n"
                + "        if (TEST_MODE) {\n"
                + "            System.out.println(\"CP_QA_TEARDOWN_STAGE \" + stage);\n"
                + "        }\n"
                + "    }";

        assertTrue(source.contains(reviewedMethod),
                "QA teardown stages must remain observability-only");
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

    @Test
    void qaNickSelectorsUseExactWholeNickMatching() {
        String configured = "client1, client10, CoronaBot$2";
        assertTrue(Crupier.configuredNickSelected(configured, "client1"));
        assertTrue(Crupier.configuredNickSelected(configured, "client10"));
        assertTrue(Crupier.configuredNickSelected(configured, "CoronaBot$2"));
        assertFalse(Crupier.configuredNickSelected(configured, "client"));
        assertFalse(Crupier.configuredNickSelected(configured, "CoronaBot$1"));
        assertFalse(Crupier.configuredNickSelected(null, "client1"));
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

    private static void assertObservabilityOnly(String source, String marker) {
        int markerOffset = source.indexOf(marker);
        assertTrue(markerOffset >= 0, "missing reviewed QA marker " + marker);
        int branchStart = source.lastIndexOf("if (GameFrame.TEST_MODE", markerOffset);
        int branchEnd = source.indexOf('}', markerOffset);
        assertTrue(branchStart >= 0 && branchEnd > markerOffset,
                "QA marker is not guarded by TEST_MODE: " + marker);
        String body = source.substring(branchStart, branchEnd + 1);
        assertTrue(body.contains("LOGGER.log("), "QA marker must only log: " + marker);
        assertFalse(body.contains("return"), "QA marker must not change control flow: " + marker);
        assertFalse(body.contains("throw"), "QA marker must not change control flow: " + marker);
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
