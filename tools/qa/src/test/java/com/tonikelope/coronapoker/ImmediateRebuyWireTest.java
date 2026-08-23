package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ImmediateRebuyWireTest {

    @Test
    void decodesOnlyCurrentCanonicalRequest() {
        assertEquals(75, ImmediateRebuyWire.parseClientRequest(
                new String[]{"GAME", "7", "REBUYNOW", "75"}));
        assertEquals(0, ImmediateRebuyWire.parseClientRequest(
                new String[]{"GAME", "7", "REBUYNOW", "0"}));

        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseClientRequest(
                new String[]{"GAME", "7", "REBUYNOW", "75", ""}));
        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseClientRequest(
                new String[]{"GAME", "+7", "REBUYNOW", "75"}));
        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseClientRequest(
                new String[]{"GAME", "7", "REBUYNOW", "-1"}));
        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseClientRequest(
                new String[]{"GAME", "7", "REBUYNOW", " 75"}));
    }

    @Test
    void decodesOnlyCurrentCanonicalHostRelay() {
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        ImmediateRebuyWire.Relay relay = ImmediateRebuyWire.parseHostRelay(
                new String[]{"GAME", "8", "REBUYNOW", nick, "50"});
        assertEquals("alice", relay.nick());
        assertEquals(50, relay.amount());
        assertTrue(!relay.denied());

        ImmediateRebuyWire.Relay denied = ImmediateRebuyWire.parseHostRelay(
                new String[]{"GAME", "9", "REBUYDENIED", nick, "3"});
        assertTrue(denied.denied());
        assertEquals(3, denied.amount());

        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseHostRelay(
                new String[]{"GAME", "8", "REBUYNOW", nick, "50", ""}));
        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseHostRelay(
                new String[]{"GAME", "8", "REBUYNOW", "YQ", "50"}));
        assertThrows(IllegalArgumentException.class, () -> ImmediateRebuyWire.parseHostRelay(
                new String[]{"GAME", "8", "REBUYDENIED", nick, "0"}));
    }

    @Test
    void dispatchersCloseMalformedRebuyInsteadOfDroppingIt() throws Exception {
        Path root = locateRoot();
        String participant = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Participant.java")).replace("\r\n", "\n");
        int hostCase = participant.indexOf("case \"REBUYNOW\":");
        int hostEnd = participant.indexOf("case \"SHOWCARDS\":", hostCase);
        String host = participant.substring(hostCase, hostEnd);
        assertTrue(host.contains("ImmediateRebuyWire.parseClientRequest(partes_comando)"));
        assertTrue(host.contains("exitAndCloseSocket()"));

        String waiting = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java")).replace("\r\n", "\n");
        int relayCase = waiting.indexOf("case \"REBUYNOW\":", waiting.indexOf("if (isPartida_empezada())"));
        int relayEnd = waiting.indexOf("case \"SHOWCARDS\":", relayCase);
        String client = waiting.substring(relayCase, relayEnd);
        assertTrue(client.contains("ImmediateRebuyWire.parseHostRelay(partes_comando)"));
        assertTrue(client.contains("closeCriticalHostChannel()"));
    }

    @Test
    void rebuyAndNextHandBoundaryPreserveOneGlobalCausalOrder() throws Exception {
        Path root = locateRoot();
        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");
        String waiting = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java")).replace("\r\n", "\n");

        assertTrue(crupier.contains("synchronized (lock_game_broadcast)"),
                "host GAME broadcasts must have one cross-peer order");
        assertTrue(crupier.contains("commitPendingRebuys(rebuy_now, rebuy_committed)"),
                "START must seal one rebuy generation before encoding balances");
        assertTrue(crupier.contains("remote_rebuy_barrier.complete(arrivalSequence)"));
        assertTrue(crupier.indexOf("awaitRemoteRebuyBarrier(throughSequence)")
                < crupier.indexOf("acceptNextHandBalanceSnapshot(partes[3]"),
                "client must apply received rebuys before validating START balances");
        assertTrue(waiting.contains("rbCrupier.registerRemoteRebuyRelay(rbSequence)"));
        assertTrue(waiting.contains("startCrupier.enqueueRemoteRebuyBarrier(rebuyBoundary"));
    }

    @Test
    void boundaryCommitKeepsLateRebuyForFollowingHand() {
        Map<String, Integer> pending = new LinkedHashMap<>();
        Map<String, Integer> committed = new LinkedHashMap<>();
        pending.put("alice", 50);

        Crupier.commitPendingRebuys(pending, committed);
        pending.put("bob", 75);

        assertEquals(Map.of("alice", 50), committed);
        assertEquals(Map.of("bob", 75), pending);

        Crupier.commitPendingRebuys(pending, committed);
        assertEquals(Map.of("bob", 75), committed);
        assertTrue(pending.isEmpty());
    }

    @Test
    void committedRebuyCannotSurviveItsPlayerLeavingTheTable() {
        Crupier.validateCommittedRebuyTargets(Map.of("alice", 50),
                java.util.Set.of("alice", "bob"));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.validateCommittedRebuyTargets(
                        Map.of("alice", 50), java.util.Set.of("bob")));
    }

    @Test
    void onlyExplicitlyReaddedZeroStackRecoveryBotsNeedFunding() {
        RecoveryBalanceReconciler.Result balances
                = RecoveryBalanceReconciler.parseObserver(
                        "Q29yb25hQm90JDE=|0.00|10|0@"
                        + "Q29yb25hQm90JDI=|17.50|10|0@"
                        + "c2VydmVy|12.50|10|0",
                        Set.of("CoronaBot$1", "CoronaBot$2", "server"));
        assertTrue(balances.isOk());
        assertEquals(Set.of("CoronaBot$1"),
                Crupier.recoveryLobbyBotsNeedingBuyin(
                        balances.balances(),
                        Set.of("CoronaBot$1", "CoronaBot$2", "CoronaBot$3")));
    }

    @Test
    void zeroStackRecoveryBotSurvivesUntilItsQueuedRebuyIsApplied() {
        assertTrue(Crupier.shouldExitSpectatorBot(
                true, false, true, 0d, true, false));
        assertTrue(!Crupier.shouldExitSpectatorBot(
                true, false, true, 0d, true, true));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
                return path;
            }
        }
        throw new IllegalStateException("repository root not found");
    }
}
