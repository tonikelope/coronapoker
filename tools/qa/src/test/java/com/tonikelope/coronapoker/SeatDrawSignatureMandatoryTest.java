package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class SeatDrawSignatureMandatoryTest {

    @Test
    void everyHumanCommitRequiresAnIdentitySignature() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");

        int hostStart = source.indexOf("private String[] hostSeatDrawCommitReveal()");
        int hostEnd = source.indexOf("private int collectSeatResponses(", hostStart);
        String host = source.substring(hostStart, hostEnd);
        assertTrue(host.contains("localSig == null"), "host can start without its signature");
        assertTrue(host.contains("pub == null || sig == null"), "host accepts an unsigned remote commit");

        int clientStart = source.indexOf("private String[] clientSeatDraw()", hostEnd);
        int clientEnd = source.indexOf("private ArrayList<String> liveRemoteHumanNicks()", clientStart);
        String client = source.substring(clientStart, clientEnd);
        assertTrue(client.contains("mySig == null"), "client can contribute without its signature");
        assertTrue(client.contains("pub == null || sig == null"), "client accepts unsigned relayed commits");
        assertTrue(client.contains("seatCommitContributorsMatchRoster"),
                "client does not bind the commit table to every human in the roster");
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
