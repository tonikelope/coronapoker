package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DealerDatabasePortWiringTest {

    @Test
    void canonicalDealerUsesInjectedDatabaseAndClassicAdapterPreservesLock() throws Exception {
        Path root = root();
        String dealer = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String adapter = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/SwingGameDatabase.java"));

        assertTrue(dealer.contains("GameDatabase gameDatabase"));
        assertTrue(dealer.contains("synchronized (game_database.lock())"));
        assertTrue(dealer.contains("game_database.connection()"));
        assertFalse(dealer.contains("GameFrame.SQL_LOCK"));
        assertFalse(dealer.contains("Helpers.getSQLITE()"));
        assertTrue(adapter.contains("return GameFrame.SQL_LOCK"));
        assertTrue(adapter.contains("return Helpers.getSQLITE()"));
    }

    private static Path root() {
        for (Path path = Path.of("").toAbsolutePath(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) return path;
        }
        throw new IllegalStateException("CoronaPoker root not found");
    }
}
