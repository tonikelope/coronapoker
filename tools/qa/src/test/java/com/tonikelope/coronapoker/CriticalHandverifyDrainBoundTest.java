package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalHandverifyDrainBoundTest {

    @Test
    public void queueDrainHasAFiniteBatchBeforeRecheckingDeadlines() {
        assertTrue(Crupier.criticalHandverifyDrainBatchLimit() > 0);
        assertTrue(Crupier.criticalHandverifyDrainBatchLimit() <= 1024);
    }

    @Test
    public void bothTriggerAndReceiptLoopsUseTheBoundAndStrictIngress() throws IOException {
        Path root = locateRoot();
        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String participant = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java"));
        String waiting = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java"));

        assertTrue(count(crupier, "drainedHandverify < CRITICAL_HANDVERIFY_DRAIN_BATCH") >= 2);
        assertTrue(participant.contains("partes_comando.length == 5"));
        assertTrue(participant.contains("HandverifyReceiptEnvelope.parse(partes_comando)"));
        assertTrue(waiting.contains("case \"HANDVERIFY\":"));
        assertTrue(waiting.contains("HandverifyReceiptEnvelope.parse(partes_comando)"));
        assertTrue(waiting.contains("Invalid critical HANDVERIFY; closing host channel"));
        assertTrue(crupier.contains("handverify_trigger_received.set(false)"));
        assertTrue(crupier.contains("handverify_receipts_received.clear()"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
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
