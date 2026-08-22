package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class VoluntaryExitShowdownProofTest {

    @Test
    void allInClientPublishesItsSignedPocketBeforeLeaving() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/GameFrame.java"));
        String exitHandler = slice(source,
                "private void exit_menuActionPerformed(",
                "private void acerca_menuActionPerformed(");

        int reveal = exitHandler.indexOf("buildLocalExitCommand()");
        int markExit = exitHandler.indexOf("getLocalPlayer().setExit()", reveal);
        int sendExit = exitHandler.indexOf("sendGAMECommandToServer(exitCommand", reveal);

        assertTrue(reveal >= 0,
                "voluntary client exit must publish an all-in showdown proof");
        assertTrue(markExit > reveal,
                "the signed pocket proof must be delivered before the local player is marked exited");
        assertTrue(sendExit > markExit,
                "EXIT must remain ordered after proof delivery and the local exit transition");
    }

    @Test
    void hostRetainsVerifiedProofForAnExitedAllIn() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String verifier = slice(source,
                "private boolean verifyAndStoreShowdownKey(",
                "private void checkJugadasParciales(");
        assertTrue(verifier.contains("verified_showdown_signatures.put(nick, sigB64)"),
                "a verified remote signature must survive until atomic POTCARDS construction");

        String hostShowdown = slice(source,
                "private void solicitarYRecibirCartasVisuales(",
                "private void failShowdownWaitIfUnexpected(");
        assertTrue(hostShowdown.contains("reuseExitedShowdownProof"),
                "an exited all-in must reuse its already verified proof instead of being rejected");
    }

    @Test
    void abruptAllInDisconnectWithoutProofCancelsForRecovery() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String hostShowdown = slice(source,
                "private void solicitarYRecibirCartasVisuales(",
                "private void failShowdownWaitIfUnexpected(");

        assertTrue(hostShowdown.contains(
                "cancelarManoYDevolverApuestas(\"peer.unlock_no_testament\")"),
                "an abrupt all-in disconnect without retained proof must MISDEAL/refund");
        assertTrue(hostShowdown.contains("participant == null || participant.isExit()"),
                "the pending-proof wait must re-check the live participant state");
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from,
                "expected source slice " + start + " .. " + end);
        return source.substring(from, to);
    }

    private static Path locateRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++, cursor = cursor.getParent()) {
            if (Files.exists(cursor.resolve("src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
                return cursor;
            }
        }
        throw new IllegalStateException("CoronaPoker root not found");
    }
}
