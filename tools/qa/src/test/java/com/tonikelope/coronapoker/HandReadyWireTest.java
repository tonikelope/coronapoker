package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class HandReadyWireTest {

    @Test
    void acceptsOnlyTheImmediateNextHand() {
        assertTrue(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "7", "HAND_READY", "4"}, 3));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "7", "HAND_READY", "2147483647"}, 3));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "7", "HAND_READY", "3"}, 3));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "7", "HAND_READY", "+4"}, 3));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "7", "HAND_READY", "4", ""}, 3));
        assertFalse(Crupier.handReadyMatchesNextHand(
                new String[]{"GAME", "7", "HAND_READY", "1"}, Integer.MAX_VALUE));
    }

    @Test
    void participantChecksBoundaryBeforeMutatingReadiness() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java")).replace("\r\n", "\n");
        int start = source.indexOf("case \"HAND_READY\":");
        int end = source.indexOf("case \"EXIT\":", start);
        String handler = source.substring(start, end);
        int check = handler.indexOf("handReadyMatchesNextHand");
        int mutation = handler.indexOf("this.new_hand_ready =");
        assertTrue(check >= 0 && mutation > check);
        assertTrue(handler.contains("exitAndCloseSocket()"));
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
