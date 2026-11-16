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

        int hostDeal = crupier.indexOf("private boolean enviarCartasJugadoresRemotos(");
        int megapacket = crupier.indexOf("broadcastGAMECommandFromServer(\"MEGAPACKET#", hostDeal);
        int proofBarrier = crupier.indexOf("if (!verifyAndBroadcastShuffleBeforeDeal(", hostDeal);
        int firstUnlock = crupier.indexOf("extendPocketChainsForSigner(", hostDeal);
        assertTrue(hostDeal >= 0 && megapacket > hostDeal && proofBarrier > megapacket
                && firstUnlock > proofBarrier,
                "the host must publish the deck and its proof before starting the private deal");

        int builder = crupier.indexOf("private boolean verifyAndBroadcastShuffleBeforeDeal(");
        int fullCheck = crupier.indexOf("verifyFullChainWire(", builder);
        int broadcast = crupier.indexOf("broadcastGAMECommandFromServer(bundle, null);", builder);
        int verified = crupier.indexOf("this.dual_lock_verified_megapacket = megapacket;", builder);
        assertTrue(builder >= 0 && fullCheck > builder && broadcast > fullCheck && verified > broadcast,
                "the host must verify and deliver the bundle before publishing its verdict");
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
