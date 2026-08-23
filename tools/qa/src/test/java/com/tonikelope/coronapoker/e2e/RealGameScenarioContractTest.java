package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class RealGameScenarioContractTest {

    @Test
    void everyScenarioHasExactlyOneTimingContract() {
        Set<String> union = new HashSet<>();
        assertTrue(union.addAll(RealGameScenarioContract.AUTONOMOUS));
        assertTrue(union.addAll(RealGameScenarioContract.ACTION_GATED),
                "autonomous and action-gated scenarios overlap");
        assertTrue(union.addAll(RealGameScenarioContract.DIALOG_ORDERED),
                "dialog-ordered scenarios overlap another timing contract");
        assertEquals(RealGameScenarioContract.ALL, union);
        assertTrue(RealGameScenarioContract.ALL.containsAll(
                RealGameScenarioContract.MISDEAL_TERMINAL));
    }

    @Test
    void onlyExplicitAbruptExitScenariosPermitACompletedHandWithoutAFrame() {
        assertTrue(RealGameScenarioContract.expectsTerminalMisdeal("abrupt-exit"));
        assertTrue(RealGameScenarioContract.expectsTerminalMisdeal("dual-abrupt-exit"));
        assertTrue(RealGameScenarioContract.expectsTerminalMisdeal("mixed-exit-crash"));
        assertTrue(RealGameScenarioContract.expectsTerminalMisdeal("allin-abrupt-exit"));
        assertFalse(RealGameScenarioContract.expectsTerminalMisdeal("normal"));
        assertFalse(RealGameScenarioContract.expectsTerminalMisdeal("controlled-exit"));
        assertFalse(RealGameScenarioContract.expectsTerminalMisdeal("crash-rejoin-recover"));
    }

    @Test
    void publicRunnerAndCertificationUseTheExactJavaCatalog() throws IOException {
        Path root = repositoryRoot();
        String runner = Files.readString(root.resolve("tools/qa/run-real-game-e2e.ps1"));
        Matcher validateSet = Pattern.compile(
                "\\[ValidateSet\\((.*?)\\)\\]\\s*\\[string\\]\\$Scenario",
                Pattern.DOTALL).matcher(runner);
        assertTrue(validateSet.find(), "runner Scenario ValidateSet not found");
        assertEquals(RealGameScenarioContract.ALL,
                quotedValues(validateSet.group(1)),
                "runner and Java scenario catalogs diverged");

        String certification = Files.readString(
                root.resolve("tools/qa/run-certification.ps1"));
        Matcher names = Pattern.compile("Name\\s*=\\s*'([^']+)'")
                .matcher(certification);
        Set<String> certified = new HashSet<>();
        while (names.find()) {
            certified.add(names.group(1));
        }
        assertEquals(RealGameScenarioContract.ALL, certified,
                "certification omits or invents real-game scenarios");
    }

    @Test
    void orderedAllInGateWaitsForTheActualAllInButton() {
        assertFalse(RealGameNodeMain.actionInputReady(
                true, true, true, false, true));
        assertTrue(RealGameNodeMain.actionInputReady(
                true, false, false, true, false));
        assertTrue(RealGameNodeMain.actionInputReady(
                false, true, false, false, false));
    }

    @Test
    void armedGatePreventsTheAutomaticDriverFromStealingItsAction() {
        Set<String> armed = Set.of("7#3");

        assertTrue(RealGameNodeMain.automatedActionBlocked(armed, 7, 3));
        assertFalse(RealGameNodeMain.automatedActionBlocked(armed, 7, 2));
        assertFalse(RealGameNodeMain.automatedActionBlocked(armed, 8, 3));
    }

    @Test
    void ritCutDelaysOnlyTheSecondRemoteVote() {
        assertTrue(RealGameNodeMain.shouldGateRitVote(
                "rit-network-cut", "client2", false));
        assertFalse(RealGameNodeMain.shouldGateRitVote(
                "rit-network-cut", "client1", false));
        assertFalse(RealGameNodeMain.shouldGateRitVote(
                "rit-network-cut", "client2", true));
        assertFalse(RealGameNodeMain.shouldGateRitVote(
                "allin-rit", "client2", false));
    }

    @Test
    void lobbyGateRequiresEveryExpectedSeat() {
        assertFalse(RealGameNodeMain.lobbyTopologyComplete(2, 3, 4, 4, false));
        assertFalse(RealGameNodeMain.lobbyTopologyComplete(3, 3, 3, 4, false));
        assertTrue(RealGameNodeMain.lobbyTopologyComplete(3, 3, 4, 4, false));
        assertFalse(RealGameNodeMain.lobbyTopologyComplete(3, 3, 5, 4, false));

        assertTrue(RealGameNodeMain.lobbyTopologyComplete(3, 3, 3, 4, true));
        assertTrue(RealGameNodeMain.lobbyTopologyComplete(3, 3, 4, 4, true));
        assertFalse(RealGameNodeMain.lobbyTopologyComplete(3, 3, 5, 4, true));
    }

    @Test
    void expectedMisdealAllowsOnlyItsOwnErrorDialog() {
        long allowance = RealGameLoopbackE2EIT.expectedMisdealDialogAllowance(0, 1, 0);
        assertEquals(1, allowance, "MISDEAL must authorize its imminent public dialog");
        assertFalse(2 <= allowance, "a second unrelated error dialog must remain terminal");

        allowance = RealGameLoopbackE2EIT.expectedMisdealDialogAllowance(allowance, 1, 1);
        assertEquals(1, allowance, "observing the same dialog must not widen the allowance");

        allowance = RealGameLoopbackE2EIT.expectedMisdealDialogAllowance(allowance, 2, 1);
        assertEquals(2, allowance, "a second observed MISDEAL authorizes one second dialog");
    }

    @Test
    void intentionalSocketCutAllowsOnlyOneCausallyBoundWriteFailure() {
        assertFalse(RealGameScenarioContract.hasUnexpectedClientWriteFailure(List.of(
                "CP_E2E_SOCKET_DROP_ARMED role=client",
                "Client write failed - socket dead, forcing reconnect",
                "CP_E2E_SOCKET_DROP_REQUESTED role=client",
                "Reconnected successfully to server")));

        assertTrue(RealGameScenarioContract.hasUnexpectedClientWriteFailure(List.of(
                "Client write failed - socket dead, forcing reconnect")),
                "an unprovoked write failure must remain terminal");
        assertTrue(RealGameScenarioContract.hasUnexpectedClientWriteFailure(List.of(
                "CP_E2E_SOCKET_DROP_ARMED role=client",
                "Reconnected successfully to server",
                "Client write failed - socket dead, forcing reconnect")),
                "a late write after reconnect must remain terminal");
        assertTrue(RealGameScenarioContract.hasUnexpectedClientWriteFailure(List.of(
                "CP_E2E_SOCKET_DROP_ARMED role=client",
                "Client write failed - socket dead, forcing reconnect",
                "Client write failed - socket dead, forcing reconnect",
                "Reconnected successfully to server")),
                "a second failure in one cut window must remain terminal");
    }

    private static Set<String> quotedValues(String text) {
        Matcher values = Pattern.compile("'([^']+)'").matcher(text);
        Set<String> result = new HashSet<>();
        while (values.find()) {
            result.add(values.group(1));
        }
        return result;
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("tools/qa/run-certification.ps1"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker repository root not found");
    }
}
