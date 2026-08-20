package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class RecoveryWireWiringTest {
    @Test
    public void recoverDataUsesTypedV1CodecAndNoJavaSerialization() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String receiveState = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/RecoveryReceiveState.java"));
        assertTrue(source.contains("new RecoveryReceiveState(GameFrame.UGI)"));
        assertTrue(receiveState.contains("RecoverySnapshotV1.decode(wire, expectedSession)"));
        assertTrue(source.contains("snapshot.value().encode()"));
        assertFalse(source.contains("ObjectInputStream"));
        assertFalse(source.contains("ObjectOutputStream"));
    }

    @Test
    public void balanceEvidenceIsReconciledBeforeHostShellsOrPlayerMutation() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int recoveryMethod = source.indexOf("void recuperarDatosClavePartida()");
        int evidence = source.indexOf(
                "LocalRecoveryBalanceEvidence localEvidence = readLocalRecoverBalanceEvidence()",
                recoveryMethod);
        int receive = source.indexOf("map = recibirDatosClaveRecuperados()", evidence);
        int reconcile = source.indexOf("RecoveryBalanceReconciler.reconcileExact(", receive);
        int syncShells = source.indexOf("sqlSyncRecoveryShells(map)", receive);
        int mutateStack = source.indexOf("jug.setStack(stack)", reconcile);
        int reject = source.indexOf("balance reconciliation failed", reconcile);
        int stop = source.indexOf("setFin_de_la_transmision(true)", reject);
        int close = source.indexOf("closeClientSocket()", stop);

        assertTrue(evidence >= 0 && evidence < receive);
        assertTrue(receive < reconcile && reconcile < syncShells);
        assertTrue(syncShells < mutateStack);
        assertTrue(reject < stop && stop < close && close < syncShells);
        assertFalse(source.contains("falling back to host"));
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
