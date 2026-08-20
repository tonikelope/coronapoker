package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RitVoteCloseAdmissionWiringTest {

    @Test
    void clientParsesAndAdmitsTheCanonicalResultOnlyOncePerHand() throws IOException {
        Path root = locateRoot();
        String waitingRoom = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java"));
        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));

        assertTrue(waitingRoom.contains("RitVoteCloseEnvelope.parse(partes_comando)"));
        assertTrue(waitingRoom.contains("acceptRitVoteCloseOnce(result.agreed())"));
        assertTrue(crupier.contains("public synchronized void acceptRitVoteCloseOnce(boolean agreed)"));
        assertTrue(crupier.contains("RIT_VOTE_CLOSE overrides this client's vote"));
        assertTrue(crupier.contains("this.rit_vote_close_received = false;"));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("CoronaPoker root not found from " + start);
    }
}
