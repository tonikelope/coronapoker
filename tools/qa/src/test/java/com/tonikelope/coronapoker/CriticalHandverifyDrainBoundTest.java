package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalHandverifyDrainBoundTest {

    @Test
    public void queueDrainIsBoundedByAcceptedSnapshotNotAnArbitraryPrefix() {
        GameCommandMailbox mailbox = new GameCommandMailbox(4096);
        for (int i = 0; i < 256; i++) {
            mailbox.add("GAME#" + i + "#ACTION#deferred");
        }
        mailbox.add("GAME#257#HANDVERIFY");

        assertEquals(257, Crupier.criticalHandverifySnapshotSize(mailbox));
    }

    @Test
    public void bothTriggerAndReceiptLoopsUseTheBoundAndStrictIngress() throws IOException {
        Path root = locateRoot();
        String crupier = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        assertTrue(count(crupier, "criticalHandverifySnapshotSize(this.getReceived_commands())") >= 2);
        assertTrue(count(crupier, "drainedHandverify < scanLimit") >= 2);
        assertTrue(crupier.contains("partes.length == 5"));
        assertTrue(crupier.contains("HandverifyReceiptEnvelope.parse(partes)"));
        assertTrue(crupier.contains("Malformed critical HANDVERIFY receipt; closing source"));
        assertTrue(count(crupier, "this.received_commands.reject(comando)") >= 3);
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
