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
    void coreDispatcherChecksBoundaryBeforeMutatingReadiness() throws Exception {
        Path root = locateRoot();
        String source = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int start = source.indexOf("public void acceptRemoteHandReady(");
        int end = source.indexOf("public double getApuestas()", start);
        String handler = source.substring(start, end);
        int check = handler.indexOf("handReadyMatchesNextHand");
        int mutation = handler.indexOf("peer.setNew_hand_ready(");
        assertTrue(check >= 0 && mutation > check);
        String factory = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/CoreGameTableFactory.java"));
        int dispatchStart = factory.indexOf(
                "if (lobby.host() && command.command().startsWith(\"HAND_READY#\"))");
        int dispatchEnd = factory.indexOf(
                "if (!lobby.host()", dispatchStart);
        assertTrue(dispatchStart >= 0 && dispatchEnd > dispatchStart);
        String dispatch = factory.substring(dispatchStart, dispatchEnd);
        int acceptance = dispatch.indexOf(
                "dealer.acceptRemoteHandReady(command.peerNickname(), envelope)");
        int invalidClose = dispatch.indexOf("context.channel().close()", acceptance);
        assertTrue(acceptance >= 0 && invalidClose > acceptance);
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
                return path;
            }
        }
        throw new IllegalStateException("repository root not found");
    }
}
