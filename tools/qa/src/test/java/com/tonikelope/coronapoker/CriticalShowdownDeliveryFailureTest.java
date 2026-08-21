package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CriticalShowdownDeliveryFailureTest {

    @Test
    void invalidOrMissingPotcardsCannotFallThroughToPayout() throws Exception {
        Path root = locateRoot();
        String source = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");
        String receive = slice(source, "private void recibirCartasResistencia(",
                "// Waits (timed-wait robust", 0);
        assertTrue(receive.contains("PotCardsEnvelope.parse("));
        assertTrue(receive.contains("rejectCriticalShowdownMessage(comando"));
        assertTrue(receive.contains("closeHostAfterCriticalShowdownFailure();"));
        assertFalse(receive.contains("showdown reveals incomplete"),
                "timeout must close/recover, never continue toward partial settlement");

        String settle = slice(source, "default:\n                                        // Everyone shows",
                "if (this.bote.getSidePot() == null)", 0);
        assertTrue(settle.contains("if (isFin_de_la_transmision() || this.termination_pending"),
                "a post-barrier POTCARDS failure must be checked before the first payout calculation");

        String waitingRoom = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java")).replace("\r\n", "\n");
        String showcards = slice(waitingRoom, "case \"SHOWCARDS\":",
                "case \"RABBIT_FLOP_PIECE\":", 0);
        assertTrue(showcards.contains("boolean revealed ="));
        assertTrue(showcards.contains("if (!revealed)"));
        assertTrue(showcards.contains("closeCriticalHostChannel();"),
                "a rejected SHOWCARDS from the authenticated host must close, not just log");

        String hostBuild = slice(source, "private void solicitarYRecibirCartasVisuales(",
                "private boolean verifyAndStoreShowdownKey(", 0);
        assertTrue(hostBuild.contains("missing mandatory POTCARDS proof"));
        assertTrue(hostBuild.contains("containTableFailure("),
                "the host must preserve the open hand if it cannot build the full envelope");

        String missingRevealTimeout = slice(hostBuild,
                "System.currentTimeMillis() - start_time > SHOWDOWN_DELIVERY_TIMEOUT_MS",
                "synchronized (this.getReceived_commands())", 0);
        assertTrue(missingRevealTimeout.contains("containTableFailure("),
                "a missing contender reveal must preserve the hand for recovery");
        assertFalse(missingRevealTimeout.contains("markExitAndNotify("),
                "a timeout cannot remove a contender and then settle without that hand");
        assertFalse(missingRevealTimeout.contains("pendientes.clear()"),
                "a timeout cannot manufacture completion of the mandatory reveal set");
    }

    private static String slice(String source, String startToken, String endToken, int from) {
        int start = source.indexOf(startToken, from);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start, "source slice not found");
        return source.substring(start, end);
    }

    private static Path locateRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("src/main/java"))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
