package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class SeatDrawHostFrameShapeTest {

    @Test
    void acceptsOnlyExactCurrentHostSeatDrawShapes() {
        assertTrue(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "1", "SEAT_DRAW_BEGIN", "nonce", "2", "a", "b"}));
        assertTrue(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "2", "SEAT_COMMITS", "nonce", "1", "a", "commit", "sig"}));
        assertTrue(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "3", "SEAT_REVEALS", "nonce", "1", "a", "reveal"}));
        assertTrue(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "4", "SEATS", "1", "a"}));

        assertFalse(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "1", "SEAT_DRAW_BEGIN", "nonce", "2", "a"}));
        assertFalse(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "2", "SEAT_COMMITS", "nonce", "1", "a", "commit", "sig", ""}));
        assertFalse(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "3", "SEAT_REVEALS", "nonce", "-1"}));
        assertFalse(Crupier.seatDrawHostFrameHasCurrentShape(
                new String[]{"GAME", "4", "SEATS", "+1", "a"}));
    }

    @Test
    void hostAndClientRejectMalformedSeatDrawOccurrences() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");

        int collectStart = source.indexOf("private int collectSeatResponses(");
        int collectEnd = source.indexOf("private String[] clientSeatDraw()", collectStart);
        String host = source.substring(collectStart, collectEnd);
        assertTrue(host.contains("comando.split(\"#\", -1)"));
        assertTrue(host.contains("this.received_commands.reject(comando)"));
        assertTrue(host.contains("protocolViolation = true"));
        assertTrue(host.contains("if (protocolViolation)"));
        assertTrue(host.contains("return SEAT_COLLECT_ABORT"));

        int clientStart = collectEnd;
        int clientEnd = source.indexOf("private ArrayList<String> liveRemoteHumanNicks()", clientStart);
        String client = source.substring(clientStart, clientEnd);
        assertTrue(client.contains("comando.split(\"#\", -1)"));
        assertTrue(client.contains("!seatDrawHostFrameHasCurrentShape(p)"));
        assertTrue(client.contains("rejectCriticalSeatDrawHostCommand(comando"));
        assertTrue(client.contains("rejectCriticalSeatDrawHostCommand(null"));
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
