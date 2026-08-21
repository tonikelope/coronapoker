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

    @Test
    public void missingOrInvalidActionDataTerminatesRecoveryInsteadOfBecomingEmpty() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int method = source.indexOf("private String recibirAccionesRecuperadas()");
        int state = source.indexOf("new RecoveryActionReceiveState()", method);
        int failed = source.indexOf("RecoveryActionReceiveState.Status.FAILED", state);
        int recover = source.indexOf("setForce_recover(true)", failed);
        int pending = source.indexOf("setTerminationPending()", recover);
        int finished = source.indexOf("setFin_de_la_transmision(true)", pending);
        int close = source.indexOf("closeClientSocket()", finished);
        int result = source.indexOf("receiveState.isSuccess() ? receiveState.actions() : null", close);
        int receive = source.indexOf("recuperarAccionesLocales();");
        int recoverAbort = source.indexOf("if (isFin_de_la_transmision())", receive);
        int handAbortComment = source.indexOf("Any fail-closed recovery path must stop NUEVA_MANO here");
        int handAbort = source.indexOf("if (isFin_de_la_transmision())", handAbortComment);
        int handAbortReturn = source.indexOf("return false;", handAbort);
        int localReplay = source.indexOf("private void recuperarAccionesLocales()");
        int batchDecode = source.indexOf("RecoveredActionBatch.decode(datos)", localReplay);
        int batchFailure = source.indexOf("if (!decodedBatch.isOk())", batchDecode);
        int missingReason = source.indexOf("peer.recovery_action_data_unavailable", batchFailure);
        int cancel = source.indexOf("cancelarManoYDevolverApuestas(", missingReason);
        int sendActions = source.indexOf("enviarAccionesRecuperadas(pendientes, datos)", cancel);

        assertTrue(method >= 0 && method < state);
        assertTrue(state < failed && failed < recover && recover < pending);
        assertTrue(pending < finished && finished < close && close < result);
        assertTrue(receive >= 0 && receive < recoverAbort);
        assertTrue(handAbortComment >= 0 && handAbortComment < handAbort && handAbort < handAbortReturn);
        assertTrue(localReplay >= 0 && localReplay < batchDecode);
        assertTrue(batchDecode < batchFailure && batchFailure < missingReason && missingReason < cancel);
        assertTrue(cancel < sendActions, "host must validate the complete SQL batch before broadcast");
        assertFalse(source.substring(localReplay, cancel).contains(
                "datos != null && !datos.isEmpty()"));
        assertFalse(source.contains("ACTIONDATA malformed dropped"));
        assertFalse(source.contains("recovery dialog closes via the empty-queue branch"));
    }

    @Test
    public void missingOrInvalidRecoverDataTerminatesBeforeFreshHandFallback() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int receiver = source.indexOf("private HashMap<String, Object> recibirDatosClaveRecuperados()");
        int failed = source.indexOf("RecoveryReceiveState.Status.FAILED", receiver);
        int force = source.indexOf("setForce_recover(true)", failed);
        int pending = source.indexOf("setTerminationPending()", force);
        int finish = source.indexOf("setFin_de_la_transmision(true)", pending);
        int close = source.indexOf("closeClientSocket()", finish);
        int result = source.indexOf("receiveState.isSuccess() ? receiveState.snapshot().toMap() : null", close);
        int caller = source.indexOf("map = recibirDatosClaveRecuperados()");
        int nullBranch = source.indexOf("if (map == null)", caller);
        int callerFinish = source.indexOf("setFin_de_la_transmision(true)", nullBranch);
        int callerReturn = source.indexOf("return;", callerFinish);

        assertTrue(receiver >= 0 && receiver < failed && failed < force);
        assertTrue(force < pending && pending < finish && finish < close && close < result);
        assertTrue(caller < nullBranch && nullBranch < callerFinish && callerFinish < callerReturn);
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
