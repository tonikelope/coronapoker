package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BettingReducerFailureContainmentTest {

    @Test
    void reducerDivergencePreservesContendersAndAbortsBeforeSettlement() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"))
                .replace("\r\n", "\n");
        String failure = slice(source,
                "if (!applyBettingRoundAction(current_player, decision)) {",
                "Bot.OpponentTracker stats", 0);

        assertTrue(failure.contains("containTableFailure("),
                "a reducer divergence must terminate the table and preserve the open hand");
        assertFalse(failure.contains("resisten.clear()"),
                "a reducer failure cannot manufacture a no-contender settlement");
    }

    private static String slice(String source, String startToken, String endToken, int from) {
        int start = source.indexOf(startToken, from);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start, "source slice not found");
        return source.substring(start, end);
    }

    private static Path locateRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("src/main/java"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
