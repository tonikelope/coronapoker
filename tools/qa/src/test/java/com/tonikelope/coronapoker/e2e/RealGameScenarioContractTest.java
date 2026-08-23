package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        Matcher profileLabels = Pattern.compile("Label\\s*=\\s*'([^']+)'")
                .matcher(certification);
        Set<String> certifiedProfiles = new HashSet<>();
        while (profileLabels.find()) {
            assertTrue(certifiedProfiles.add(profileLabels.group(1)),
                    "duplicate certification profile label: " + profileLabels.group(1));
        }

        String testingManual = Files.readString(root.resolve("docs/TESTING.md"));
        int tableStart = testingManual.indexOf("Scenario contracts:");
        int tableEnd = testingManual.indexOf("The real-game runner defaults", tableStart);
        assertTrue(tableStart >= 0 && tableEnd > tableStart,
                "public scenario table boundaries not found");
        Matcher documentedRows = Pattern.compile("(?m)^\\| `([^`]+)` \\|")
                .matcher(testingManual.substring(tableStart, tableEnd));
        Set<String> documented = new HashSet<>();
        while (documentedRows.find()) {
            documented.add(documentedRows.group(1));
        }
        assertEquals(RealGameScenarioContract.ALL, documented,
                "docs/TESTING.md scenario catalog diverged");

        int matrixStart = testingManual.indexOf("| Certification profile |");
        int matrixEnd = testingManual.indexOf("Each row runs once", matrixStart);
        assertTrue(matrixStart >= 0 && matrixEnd > matrixStart,
                "public certification matrix boundaries not found");
        Matcher matrixRows = Pattern.compile("(?m)^\\| `([^`]+)` \\|")
                .matcher(testingManual.substring(matrixStart, matrixEnd));
        Set<String> documentedProfiles = new HashSet<>();
        while (matrixRows.find()) {
            assertTrue(documentedProfiles.add(matrixRows.group(1)),
                    "duplicate documented certification profile: " + matrixRows.group(1));
        }
        assertEquals(certifiedProfiles, documentedProfiles,
                "docs/TESTING.md certification matrix diverged");

        Matcher quickBlock = Pattern.compile(
                "\\$quickLabels\\s*=\\s*@\\((.*?)\\)\\s*\\$scenarioProfiles",
                Pattern.DOTALL).matcher(certification);
        assertTrue(quickBlock.find(), "quick certification profile block not found");
        Set<String> quickProfiles = quotedValues(quickBlock.group(1));

        Matcher profileRows = Pattern.compile(
                "@\\{\\s*Label\\s*=\\s*'([^']+)';\\s*Name\\s*=\\s*'([^']+)';"
                + "\\s*Clients\\s*=\\s*(\\d+);\\s*Bots\\s*=\\s*(\\d+);"
                + "\\s*Hands\\s*=\\s*([^\\s}]+)\\s*}")
                .matcher(certification);
        Map<String, CertificationProfile> profiles = new HashMap<>();
        while (profileRows.find()) {
            CertificationProfile profile = new CertificationProfile(
                    Integer.parseInt(profileRows.group(3)),
                    Integer.parseInt(profileRows.group(4)),
                    profileRows.group(5));
            assertTrue(profiles.put(profileRows.group(1), profile) == null,
                    "duplicate parsed certification profile: " + profileRows.group(1));
        }
        assertEquals(certifiedProfiles, profiles.keySet(),
                "unable to parse every certification profile topology");

        Matcher topologyRows = Pattern.compile(
                "(?m)^\\| `([^`]+)` \\| ([^|]+) \\| ([^|]+) \\| ([^|]+) \\| ([^|]+) \\|")
                .matcher(testingManual.substring(matrixStart, matrixEnd));
        Map<String, List<String>> documentedTopologies = new HashMap<>();
        while (topologyRows.find()) {
            documentedTopologies.put(topologyRows.group(1), List.of(
                    topologyRows.group(2).trim(),
                    topologyRows.group(3).trim(),
                    topologyRows.group(4).trim(),
                    topologyRows.group(5).trim()));
        }
        for (Map.Entry<String, CertificationProfile> entry : profiles.entrySet()) {
            List<String> actual = documentedTopologies.get(entry.getKey());
            assertTrue(actual != null, "missing documented topology for " + entry.getKey());
            assertEquals(expectedTopology(entry.getValue(), "quick",
                    quickProfiles.contains(entry.getKey())), actual.get(0),
                    "quick topology diverged for " + entry.getKey());
            assertEquals(expectedTopology(entry.getValue(), "fast", true), actual.get(1),
                    "fast topology diverged for " + entry.getKey());
            assertEquals(expectedTopology(entry.getValue(), "balanced", true), actual.get(2),
                    "balanced topology diverged for " + entry.getKey());
            assertEquals(expectedTopology(entry.getValue(), "stress", true), actual.get(3),
                    "stress topology diverged for " + entry.getKey());
        }
    }

    private static String expectedTopology(CertificationProfile profile,
            String mode, boolean included) {
        if (!included) {
            return "-";
        }
        int hands = switch (profile.handsExpression()) {
            case "$SoakHands" -> switch (mode) {
                case "quick" -> 5;
                case "fast" -> 5;
                case "balanced" -> 20;
                case "stress" -> 50;
                default -> throw new IllegalArgumentException(mode);
            };
            case "$headsUpHands" -> Set.of("quick", "fast").contains(mode) ? 5 : 20;
            case "$fullMixedHands" -> switch (mode) {
                case "quick" -> 1;
                case "fast" -> 1;
                case "balanced" -> 3;
                case "stress" -> 10;
                default -> throw new IllegalArgumentException(mode);
            };
            case "$fullHumanHands" -> mode.equals("stress") ? 3 : 1;
            default -> Integer.parseInt(profile.handsExpression());
        };
        return profile.clients() + "/" + profile.bots() + "/" + hands;
    }

    private record CertificationProfile(int clients, int bots, String handsExpression) {
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
