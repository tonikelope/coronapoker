package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CriticalVoteFailClosedWiringTest {

    @Test
    void malformedVotesAndDecisionsCloseTheirAuthenticatedSource() throws IOException {
        Path root = locateRoot();
        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String participant = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java"));

        assertTrue(participant.contains("CriticalVoteEnvelope.parseRitResponse(partes_comando)"));
        assertTrue(participant.contains("CriticalVoteEnvelope.parseStraddleResponse(partes_comando)"));
        assertTrue(crupier.contains("CriticalVoteEnvelope.parseStraddleDecision(partes)"));
        assertTrue(crupier.contains("rejectCriticalVoteCommand(cmd"));
        assertTrue(crupier.contains("Invalid critical STRADDLE_DECISION; closing host channel"));
        assertFalse(crupier.contains("// Malformed vote: ignored."));
        assertFalse(crupier.contains("// Malformed RESP: ignored."));
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
