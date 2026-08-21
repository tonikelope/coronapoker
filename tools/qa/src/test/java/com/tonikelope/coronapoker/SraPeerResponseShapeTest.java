package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class SraPeerResponseShapeTest {

    @Test
    void currentPeerResponsesRequireTheirExactFieldCount() {
        assertTrue(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "1", "DECK_CASCADE_RESP", "nick", "deck", "kp", "kc"}));
        assertTrue(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "2", "DECK_ROTATION_RESP", "nick", "pieces", "proof"}));
        assertTrue(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "3", "RESP_SRA_UNLOCK_CHAIN", "nick", "payload"}));

        assertFalse(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "1", "DECK_CASCADE_RESP", "nick", "deck", "kp"}));
        assertFalse(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "2", "DECK_ROTATION_RESP", "nick", "pieces", "proof", ""}));
        assertFalse(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "3", "RESP_SRA_UNLOCK_CHAIN", "nick"}));
        assertFalse(Crupier.sraPeerResponseHasCurrentShape(
                new String[]{"GAME", "4", "UNRELATED", "nick", "payload"}));
    }

    @Test
    void eachKnownMalformedResponseIsRejectedInsteadOfRestored() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");

        assertFailClosedConsumer(source, "private byte[] requestRemoteCascade(",
                "DECK_CASCADE_RESP", "private static String joinB64(");
        assertFailClosedConsumer(source, "private byte[] requestRemoteRotation(",
                "DECK_ROTATION_RESP", "private java.util.List<UnlockChainWire.RespItem> requestRemoteUnlockChain(");
        assertFailClosedConsumer(source, "private java.util.List<UnlockChainWire.RespItem> requestRemoteUnlockChain(",
                "RESP_SRA_UNLOCK_CHAIN", "private boolean sendGAMECommandToParticipant(");
    }

    private static void assertFailClosedConsumer(String source, String startMarker,
            String type, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, type + " consumer not found");
        String method = source.substring(start, end);

        int split = method.indexOf("cmd.split(\"#\", -1)");
        int known = method.indexOf("partes[2].equals(\"" + type + "\")");
        int shape = method.indexOf("!sraPeerResponseHasCurrentShape(partes)", known);
        int reject = method.indexOf("this.received_commands.reject(cmd)", shape);
        assertTrue(split >= 0 && split < known && known < shape && shape < reject,
                type + " must reject its malformed authenticated occurrence before parsing");
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
