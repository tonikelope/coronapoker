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
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"))
                .replace("\r\n", "\n");
        assertClientHandlerCloses(source, "processClientCascadeRequest",
                "processClientRotationRequest", "DECK_CASCADE_REQ");
        assertClientHandlerCloses(source, "processClientRotationRequest",
                "processClientUnlockChainRequest", "DECK_ROTATION_REQ");
        assertClientHandlerCloses(source, "processClientDualLockBundle",
                "decodeBase64Csv", "DUALLOCK_BUNDLE");

        String crupier = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
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

    @Test
    public void invalidCriticalResponsesCloseTheirExactAuthenticatedSource() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        assertResponseWaitRejects(source, "requestRemoteCascade", "requestRemoteRotation");
        assertResponseWaitRejects(source, "requestRemoteRotation", "requestRemoteUnlockChain");
        assertResponseWaitRejects(source, "requestRemoteUnlockChain", "sendGAMECommandToParticipant");
    }

    private static void assertClientHandlerCloses(String source, String method,
            String nextMethod, String phase) {
        int start = source.indexOf("private void " + method + "(");
        int end = source.indexOf("private ", start + 1);
        if (nextMethod != null) {
            int namedEnd = source.indexOf("private ", source.indexOf(nextMethod, start));
            if (namedEnd > start) end = namedEnd;
        }
        assertTrue(start >= 0 && end > start, method + " handler not found");
        String handler = source.substring(start, end);
        assertTrue(handler.contains("catch (Exception failure)"));
        assertTrue(handler.contains("failClientCriticalHostCommand(\"" + phase + "\", failure)"),
                phase + " failures must close the authenticated host channel");
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }

    private static void assertResponseWaitRejects(String source, String method, String nextMethod) {
        int start = source.indexOf(method + "(");
        int end = source.indexOf(nextMethod + "(", start + 1);
        assertTrue(start >= 0 && end > start, method + " source not found");
        assertTrue(source.substring(start, end).contains("this.received_commands.reject(cmd);"),
                method + " must close the exact source of an invalid critical response");
    }

    private static Path locateRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("modules/coronapoker-core/src/main/java"))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
