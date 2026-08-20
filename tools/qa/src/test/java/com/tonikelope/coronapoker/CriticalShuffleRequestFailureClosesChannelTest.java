package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalShuffleRequestFailureClosesChannelTest {

    @Test
    public void cascadeRotationAndBundleHaveNoSilentAbort() throws Exception {
        Path root = locateRoot();
        String source = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java"))
                .replace("\r\n", "\n");
        assertHandlerCloses(source, "DECK_CASCADE_REQ", "DECK_ROTATION_REQ", 5);
        assertHandlerCloses(source, "DECK_ROTATION_REQ", "DUALLOCK_BUNDLE", 6);
        assertHandlerCloses(source, "DUALLOCK_BUNDLE", "REQ_SRA_UNLOCK_CHAIN", 2);

        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        assertTrue(count(crupier, "closeHostAfterShuffleVerificationFailure();") >= 2,
                "dishonest and malformed asynchronous verdicts must close the live channel");

        int builder = crupier.indexOf("background dual-lock full-chain self-check");
        int broadcast = crupier.indexOf("broadcastGAMECommandFromServer(bundle, null);", builder);
        int verified = crupier.indexOf("this.dual_lock_verified_megapacket = bgMega;", builder);
        int builderEnd = crupier.indexOf("return true;", builder);
        assertTrue(builder >= 0 && broadcast > builder && verified > broadcast,
                "the host must not release betting before the proof bundle is broadcast and ACKed");
        assertTrue(count(crupier.substring(builder, builderEnd), "markShuffleProofFailed(bgMega);") >= 4,
                "broadcast, self-check, incomplete proof and background exception must all reject the deck");
    }

    @Test
    public void eachDealPhaseRequestIsAdmittedOnlyOnce() {
        AtomicBoolean accepted = new AtomicBoolean();
        Crupier.acceptCriticalDealPhaseOnce(accepted, "DECK_CASCADE_REQ");
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.acceptCriticalDealPhaseOnce(accepted, "DECK_CASCADE_REQ"));
    }

    private static void assertHandlerCloses(String source, String name, String next, int minimumAborts) {
        int start = source.indexOf("case \"" + name + "\":");
        int end = source.indexOf("case \"" + next + "\":", start);
        assertTrue(start >= 0 && end > start, name + " handler not found");
        String[] lines = source.substring(start, end).split("\n");
        int aborts = 0;
        for (int i = 0; i < lines.length; i++) {
            if ("return;".equals(lines[i].trim())) {
                aborts++;
                int previous = i - 1;
                while (previous >= 0 && lines[previous].trim().isEmpty()) previous--;
                assertEquals("closeCriticalHostChannel();", lines[previous].trim(),
                        name + " abort at handler line " + (i + 1) + " is silent");
            }
        }
        assertTrue(aborts >= minimumAborts, name + " coverage unexpectedly shrank");
        assertTrue(source.substring(start, end).matches(
                "(?s).*catch \\(Exception [a-zA-Z]+\\) \\{.*closeCriticalHostChannel\\(\\);.*"),
                name + " unexpected exception must close the host channel");
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
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
