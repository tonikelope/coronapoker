package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalCommunityDeliveryFailureTest {

    @Test
    public void malformedCurrentCardMessageIsRejectedInsteadOfRequeued() throws Exception {
        String method = receiveCommunitySource();
        assertTrue(method.contains("rejectCriticalCommunityMessage(comando"),
                "malformed PIECE/COMM_REVEAL must close the authenticated host source");
        assertFalse(method.contains("catch (Exception ex) {\n                        rejected.add(comando);"),
                "a malformed critical card message must never be restored for a later retry");
        assertFalse(method.contains("Dropping stale/foreign COMM_REVEAL"),
                "a wrong-phase reveal must not be silently discarded");
    }

    @Test
    public void missingPieceOrRevealHasABoundedFailClosedExit() throws Exception {
        String method = receiveCommunitySource();
        assertTrue(method.contains("COMMUNITY_DELIVERY_TIMEOUT_MS"),
                "a live malicious host must not freeze the table forever by withholding cards");
        assertTrue(method.contains("closeHostAfterCriticalCommunityTimeout();"),
                "timeout must explicitly close the host channel and enter deterministic recovery");
        assertTrue(method.contains("if (System.currentTimeMillis() >= deliveryDeadline)"),
                "a continuous flood of other recipients' pieces must not bypass the deadline");
        assertFalse(method.contains("No timeout:"),
                "the old unbounded-wait policy must be removed");
    }

    @Test
    public void ritSideBDoesNotReportSuccessWithoutItsSignedReveal() throws Exception {
        String method = ritSideBSource();
        int missingReveal = method.indexOf("if (recsig == null)");
        int broadcastFailure = method.indexOf("catch (RuntimeException ex)", missingReveal);
        int absorb = method.indexOf("absorbActionIntoChain", broadcastFailure);
        assertTrue(missingReveal >= 0,
                "SIDE-B must handle an unavailable signed COMM_REVEAL explicitly");
        String missingRevealPath = method.substring(missingReveal, broadcastFailure);
        assertTrue(missingRevealPath.contains("Failed to build SIDE-B COMM_REVEAL")
                        && missingRevealPath.contains("cancelarManoYDevolverApuestas")
                        && missingRevealPath.contains("return false;"),
                "the local signing failure must remain diagnosable");
        assertTrue(broadcastFailure >= 0 && absorb > broadcastFailure,
                "SIDE-B broadcast failure and successful absorb paths must be explicit");
        String broadcastFailurePath = method.substring(broadcastFailure, absorb);
        assertTrue(broadcastFailurePath.contains("Failed to broadcast SIDE-B COMM_REVEAL")
                        && broadcastFailurePath.contains("cancelarManoYDevolverApuestas")
                        && broadcastFailurePath.contains("return false;"),
                "the transport failure must remain diagnosable");
    }

    @Test
    public void deferredPocketDeliveryFailureCannotBeReportedAsSuccess() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"))
                .replace("\r\n", "\n");
        int sendStart = source.indexOf("private boolean sendGAMECommandToParticipant");
        int sendEnd = source.indexOf("private boolean extendPocketChainsForSigner", sendStart);
        assertTrue(sendStart >= 0 && sendEnd > sendStart,
                "the critical participant unicast must expose delivery success");
        String send = source.substring(sendStart, sendEnd);
        assertTrue(send.contains("return false;") && send.contains("return true;"),
                "critical unicast must report both transport failure and successful delivery");
        assertTrue(send.contains("tracker.register")
                        && send.contains("waitSyncConfirmations")
                        && send.contains("p.socketClose()"),
                "critical unicast must require an ACK or explicitly close its recipient");

        int releaseStart = source.indexOf("private boolean releaseDeferredStraddlerCardsHost");
        int releaseEnd = source.indexOf("private boolean awaitDeferredStraddlerCardsClient", releaseStart);
        assertTrue(releaseStart >= 0 && releaseEnd > releaseStart,
                "deferred straddler release source not found");
        String release = source.substring(releaseStart, releaseEnd);
        assertTrue(release.contains("if (!sendGAMECommandToParticipant"),
                "the host must not clear deferred state after a failed POCKET_CARDS delivery");

        int dealStart = source.indexOf("private boolean enviarCartasJugadoresRemotos");
        int dealEnd = source.indexOf("private ArrayList<String> recibirMisCartas", dealStart);
        assertTrue(dealStart >= 0 && dealEnd > dealStart,
                "initial pocket delivery source not found");
        assertTrue(source.substring(dealStart, dealEnd)
                        .contains("if (!sendGAMECommandToParticipant"),
                "a failed POCKET_DEFERRED notice must abort the deal explicitly");
    }

    private static String receiveCommunitySource() throws Exception {
        Path root = locateRoot();
        String source = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"))
                .replace("\r\n", "\n");
        int start = source.indexOf("private boolean recibirCartasComunitarias()");
        int end = source.indexOf("private ArrayList<Player> rondaApuestas", start);
        assertTrue(start >= 0 && end > start, "recibirCartasComunitarias source not found");
        return source.substring(start, end);
    }

    private static String ritSideBSource() throws Exception {
        Path root = locateRoot();
        String source = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"))
                .replace("\r\n", "\n");
        int start = source.indexOf("private boolean enviarRit2Comunitarias");
        int end = source.indexOf("private int[] cascadeAndDealCommunityPieces", start);
        assertTrue(start >= 0 && end > start, "enviarRit2Comunitarias source not found");
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
