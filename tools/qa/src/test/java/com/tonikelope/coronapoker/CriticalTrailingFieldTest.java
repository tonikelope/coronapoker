package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class CriticalTrailingFieldTest {

    @Test
    void exactShapeConsumersPreserveTrailingEmptyFields() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");

        assertPreservesTrailingFields(source, "private ArrayList<String> recibirMisCartas()",
                "public Object[] readActionFromRemotePlayer(Player jugador)");
        assertPreservesTrailingFields(source, "public Object[] readActionFromRemotePlayer(Player jugador)",
                "public int puedenApostar(ArrayList<Player> jugadores)");
        assertPreservesTrailingFields(source, "private boolean runRitVote(ArrayList<Player> resisten)",
                "public void showRitClientVoteDialog(");
        assertPreservesTrailingFields(source, "private int waitStraddleRespFromRemote(String nick)",
                "private int waitStraddleResult()");
        assertPreservesTrailingFields(source, "private boolean awaitDeferredStraddlerCardsClient(",
                "private void destaparCartaComunitaria(");
    }

    private static void assertPreservesTrailingFields(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, "missing source range for " + startMarker);
        String range = source.substring(start, end);
        assertTrue(range.indexOf("split(\"#\", -1)") == range.indexOf("split("),
                "critical consumer discards trailing empty fields: " + startMarker);
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
                return path;
            }
        }
        throw new IllegalStateException("repository root not found");
    }
}
