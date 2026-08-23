package com.tonikelope.coronapoker.e2e;

import com.tonikelope.coronapoker.Crupier;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Runs complete production host/client Crupiers over real loopback sockets. */
@Tag("real-game-e2e")
final class RealGameLoopbackE2EIT {

    @Test
    @Timeout(value = 90, unit = TimeUnit.MINUTES)
    void completesConfiguredLocalGameWithRealCrupiersAndSockets(@TempDir Path root) throws Exception {
        int clients = intProperty("qa.e2e.clients", 1, 1, 9);
        int bots = intProperty("qa.e2e.bots", 2, 0, 9);
        int hands = intProperty("qa.e2e.hands", 1, 1, 1000);
        long seed = Long.getLong("qa.e2e.seed", 23059L);
        String scenario = System.getProperty("qa.e2e.scenario", "normal");
        assertTrue(RealGameScenarioContract.isSupported(scenario),
                "unsupported qa.e2e.scenario: " + scenario);
        assertTrue(clients + bots + 1 <= 10, "host + clients + bots must fit the table");
        assertTrue(!scenario.equals("allin-rit") || bots == 0,
                "allin-rit requires zero bots so every survivor uses the forced action policy");
        assertTrue(!scenario.equals("allin-rit") || hands == 1,
                "allin-rit requires one hand because a full-stack all-in may eliminate a seat");
        assertTrue(!scenario.equals("rit-network-cut")
                || (clients == 2 && bots == 0 && hands == 1),
                "rit-network-cut requires two clients, zero bots and one hand");
        assertTrue(!scenario.equals("allin-controlled-exit")
                || (clients == 1 && bots == 0 && hands == 1),
                "allin-controlled-exit requires one client, zero bots and one hand");
        assertTrue(!scenario.equals("force-recover") || hands >= 2,
                "force-recover requires the recovered hand and a fresh following hand");
        assertTrue(!scenario.equals("double-force-recover") || hands == 4,
                "double-force-recover requires exactly four hands");
        assertTrue(!scenario.equals("crash-rejoin-recover")
                || (clients == 1 && hands == 2),
                "crash-rejoin-recover requires one client and two hands");
        assertTrue(!scenario.equals("force-recover-add-client")
                || (clients == 2 && hands == 2),
                "force-recover-add-client requires two clients and two hands");
        assertTrue(!scenario.equals("force-recover-add-two")
                || (clients == 3 && hands == 2),
                "force-recover-add-two requires three clients and two hands");
        assertTrue(!scenario.equals("force-recover-swap-client")
                || (clients == 2 && hands == 2),
                "force-recover-swap-client requires two clients and two hands");
        assertTrue(!scenario.equals("allin-single-board")
                || (clients == 1 && bots == 0 && hands == 1),
                "allin-single-board requires one client, zero bots and one hand");
        assertTrue(!scenario.equals("allin-rebuy")
                || (clients == 1 && bots == 0 && hands >= 5),
                "allin-rebuy requires one client, zero bots and at least five hands");
        assertTrue(!scenario.equals("allin-reconnect")
                || (clients == 2 && bots == 0 && hands == 1),
                "allin-reconnect requires two clients, zero bots and one hand");
        assertTrue(!scenario.equals("straddle-post")
                || (clients >= 2 && bots == 0),
                "straddle-post requires at least two clients and zero bots");
        assertTrue(!scenario.equals("straddle-network-cut")
                || (clients == 2 && bots == 0 && hands == 3),
                "straddle-network-cut requires two clients, zero bots and three hands");
        assertTrue(!scenario.equals("pause-resume") || hands >= 2,
                "pause-resume requires at least two hands");
        assertTrue(!scenario.equals("reconnect-midhand")
                || (clients >= 1 && hands >= 2),
                "reconnect-midhand requires at least one client and two hands");
        assertTrue(!scenario.equals("reconnect-twice")
                || (clients >= 2 && hands >= 3),
                "reconnect-twice requires at least two clients and three hands");
        assertTrue(!scenario.equals("reconnect-every-street")
                || (clients >= 1 && hands == 4),
                "reconnect-every-street requires at least one client and four hands");
        assertTrue(!scenario.equals("reconnect-storm")
                || (clients >= 2 && hands >= 3),
                "reconnect-storm requires at least two clients and three hands");
        assertTrue(!scenario.equals("dual-reconnect")
                || (clients >= 3 && hands >= 2),
                "dual-reconnect requires at least three clients and two hands");
        assertTrue(!scenario.equals("host-channel-flap")
                || (clients >= 3 && hands >= 2),
                "host-channel-flap requires at least three clients and two hands");
        assertTrue(!scenario.equals("reconnect-force-recover")
                || (clients == 2 && hands == 3),
                "reconnect-force-recover requires two clients and three hands");
        assertTrue(!scenario.equals("transport-chaos")
                || (clients == 3 && hands == 5),
                "transport-chaos requires exactly three clients and five hands");
        assertTrue(!scenario.equals("lifecycle-chaos")
                || (clients == 2 && hands == 7),
                "lifecycle-chaos requires exactly two clients and seven hands");
        assertTrue(!(scenario.equals("dual-abrupt-exit")
                || scenario.equals("mixed-exit-crash")) || clients >= 3,
                "compound exits require at least three clients");
        assertTrue(!scenario.equals("allin-abrupt-exit")
                || (clients == 2 && bots == 0 && hands == 1),
                "allin-abrupt-exit requires two clients, zero bots and one hand");

        List<NodeProcess> nodes = new ArrayList<>();
        try {
            int initialClients = scenario.equals("force-recover-add-client")
                    ? clients - 1 : scenario.equals("force-recover-add-two")
                            ? clients - 2 : clients;
            NodeProcess host = startNode(root.resolve("host"), "host", "server", 0,
                    initialClients, bots, hands, seed);
            nodes.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)), host.diagnostic());
            int port = host.intValueAfter("CP_E2E_READY", "port=");
            assertTrue(port >= 1 && port <= 65535,
                    "host did not publish a valid bound loopback port\n" + host.diagnostic());

            for (int i = 1; i <= initialClients; i++) {
                NodeProcess client = startNode(root.resolve("client-" + i), "client", "client" + i,
                        port, initialClients, bots, hands, seed + i);
                nodes.add(client);
                assertTrue(client.await("CP_E2E_READY", Duration.ofSeconds(60)),
                        client.diagnostic());
            }

            // Process/identity startup is intentionally outside each node's
            // topology timeout. A ten-human table creates nine isolated JVMs
            // serially and may legitimately take longer than the steady-state
            // lobby budget. Start the bounded topology checks only after every
            // peer has published READY.
            for (NodeProcess node : nodes) {
                node.send("CHECK_LOBBY");
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_E2E_LOBBY_CHECK_RELEASED",
                        Duration.ofSeconds(30)), node.diagnostic());
            }

            // The host used to start as soon as the last socket connected. The
            // parent could therefore still be preparing a destructive scenario
            // while real action drivers were already advancing hand 1. Authorize
            // startup only after every node is observable and the lobby topology
            // is complete; no scenario may depend on process-launch timing.
            assertTrue(host.await("CP_E2E_LOBBY_READY", Duration.ofSeconds(60)),
                    host.diagnostic());
            for (NodeProcess client : nodes.subList(1, nodes.size())) {
                assertTrue(client.await("CP_E2E_LOBBY_READY", Duration.ofSeconds(60)),
                        "client did not observe the complete lobby topology before start\n"
                        + client.diagnostic());
            }
            armScenarioActionGates(scenario, nodes, host);
            host.send("START_GAME");
            assertTrue(host.await("CP_E2E_GAME_START_REQUESTED", Duration.ofSeconds(30)),
                    host.diagnostic());

            if (scenario.equals("abrupt-exit")) {
                runAbruptExitScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("controlled-exit")) {
                runControlledExitScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("dual-abrupt-exit")) {
                runDualAbruptExitScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("mixed-exit-crash")) {
                runMixedExitCrashScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("allin-rit")) {
                runAllInRitScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("rit-network-cut")) {
                runRitNetworkCutScenario(nodes, host, clients + 1);
                return;
            }
            if (scenario.equals("allin-controlled-exit")) {
                runAllInControlledExitScenario(nodes, host);
                return;
            }
            if (scenario.equals("allin-abrupt-exit")) {
                runAllInAbruptExitScenario(nodes, host, clients + 1);
                return;
            }
            if (scenario.equals("allin-reconnect")) {
                runAllInReconnectScenario(nodes, host, clients + 1);
                return;
            }
            if (scenario.equals("force-recover")) {
                runForceRecoverScenario(nodes, host, clients + bots + 1, hands);
                return;
            }
            if (scenario.equals("double-force-recover")) {
                runDoubleForceRecoverScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("crash-rejoin-recover")) {
                runCrashRejoinRecoverScenario(root, nodes, host, port, clients,
                        bots, hands, seed, clients + bots + 1);
                return;
            }
            if (scenario.equals("force-recover-add-client")) {
                runForceRecoverAddClientScenario(root, nodes, host, port,
                        initialClients, clients, bots, hands, seed);
                return;
            }
            if (scenario.equals("force-recover-add-two")) {
                runForceRecoverAddClientScenario(root, nodes, host, port,
                        initialClients, clients, bots, hands, seed);
                return;
            }
            if (scenario.equals("force-recover-swap-client")) {
                runForceRecoverSwapClientScenario(root, nodes, host, port,
                        clients, bots, hands, seed);
                return;
            }
            if (scenario.equals("lifecycle-chaos")) {
                runLifecycleChaosScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("transport-chaos")) {
                runTransportChaosScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("reconnect-force-recover")) {
                runReconnectForceRecoverScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("pause-resume")) {
                runPauseResumeScenario(nodes, host);
            } else if (scenario.equals("reconnect-midhand")) {
                runReconnectScenario(nodes, host, 1);
            } else if (scenario.equals("reconnect-twice")) {
                runReconnectScenario(nodes, host, 2);
            } else if (scenario.equals("reconnect-every-street")) {
                runReconnectEveryStreetScenario(nodes, host);
            } else if (scenario.equals("reconnect-storm")) {
                runReconnectStormScenario(nodes, host);
            } else if (scenario.equals("dual-reconnect")) {
                runDualReconnectScenario(nodes, host);
            } else if (scenario.equals("host-channel-flap")) {
                runHostChannelFlapScenario(nodes, host);
            } else if (scenario.equals("straddle-network-cut")) {
                runStraddleNetworkCutScenario(nodes, host);
            }

            assertNormalSession(nodes, host, hands);
            if (scenario.equals("raise-mix")) {
                long raises = nodes.stream()
                        .mapToLong(node -> node.countContaining("CP_E2E_BET_ACTION_CLICKED"))
                        .sum();
                assertTrue(raises >= Math.min(3, hands),
                        "raise-mix did not exercise enough production bet/raise clicks: "
                        + raises + "\n" + host.diagnostic());
            }
            if (scenario.equals("allin-single-board")) {
                for (NodeProcess node : nodes) {
                    assertTrue(node.contains("CP_E2E_ALLIN_ACTION_CLICKED"),
                            "allin-single-board did not force every human seat all-in\n"
                            + node.diagnostic());
                    assertFalse(node.contains("CP_E2E_RIT_VOTE"), node.diagnostic());
                    assertFalse(node.contains("invalid atomic POTCARDS"), node.diagnostic());
                    assertFalse(node.contains("missing mandatory"), node.diagnostic());
                }
            }
            if (scenario.equals("allin-rebuy")) {
                for (int hand = 1; hand <= hands; hand++) {
                    String handToken = " hand=" + hand;
                    long realAllInClicks = nodes.stream()
                            .mapToLong(node -> node.countContainingBoth(
                            "CP_E2E_ALLIN_ACTION_CLICKED", handToken))
                            .sum();
                    assertTrue(realAllInClicks >= 1,
                            "allin-rebuy did not click a real all-in button in hand "
                            + hand + "\n" + host.diagnostic());
                }
                assertTrue(host.hasBalanceWithBuyinAbove(10),
                        "allin-rebuy completed without carrying a busted seat's rebuy "
                        + "into a later hand\n" + host.diagnostic());
            }
            if (scenario.equals("straddle-post")
                    || scenario.equals("straddle-network-cut")) {
                long accepted = nodes.stream()
                        .mapToLong(node -> node.countContaining("CP_E2E_STRADDLE_ACCEPTED"))
                        .sum();
                assertEquals(hands, accepted,
                        "not every hand exercised a production straddle dialog\n"
                        + host.diagnostic());
            }
        } finally {
            stopAllNodes(nodes);
        }
    }

    private static void stopAllNodes(List<NodeProcess> nodes) throws Exception {
        // Stopping peers one by one is itself a network fault: the surviving
        // clients legitimately reconnect while the parent spends seconds
        // waiting for each earlier JVM. Broadcast the harness stop first, then
        // reap every process. The timeout is only a stuck-process fuse; it does
        // not stand in for a game/lobby transition.
        Exception sendFailure = null;
        for (NodeProcess node : nodes) {
            if (node.isAlive()) {
                try {
                    node.send("STOP");
                } catch (IOException ex) {
                    if (sendFailure == null) {
                        sendFailure = ex;
                    } else {
                        sendFailure.addSuppressed(ex);
                    }
                }
            }
        }
        try {
            for (NodeProcess node : nodes) {
                if (node.isAlive()) {
                    node.awaitExit(Duration.ofSeconds(10));
                }
            }
            if (sendFailure != null) {
                throw sendFailure;
            }
        } finally {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                nodes.get(i).close();
            }
        }
    }

    private static void armScenarioActionGates(String scenario, List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        switch (scenario) {
            case "abrupt-exit", "controlled-exit", "dual-abrupt-exit",
                    "mixed-exit-crash", "crash-rejoin-recover" ->
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
            case "allin-controlled-exit", "allin-reconnect", "allin-abrupt-exit" ->
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
            case "pause-resume" -> armActionGate(host, 1, Crupier.PREFLOP);
            case "reconnect-midhand" -> armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
            case "reconnect-twice" -> {
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
                armActionGate(nodes.get(2), 2, Crupier.PREFLOP);
            }
            case "reconnect-storm" -> {
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
                armActionGate(nodes.get(2), 2, Crupier.PREFLOP);
            }
            case "dual-reconnect", "host-channel-flap" ->
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
            case "reconnect-every-street" -> {
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
                armActionGate(nodes.get(1), 2, Crupier.FLOP);
                armActionGate(nodes.get(1), 3, Crupier.TURN);
                armActionGate(nodes.get(1), 4, Crupier.RIVER);
            }
            case "reconnect-force-recover", "force-recover",
                    "force-recover-add-client", "force-recover-add-two",
                    "force-recover-swap-client" ->
                armActionGate(host, 1, Crupier.PREFLOP);
            case "double-force-recover" -> {
                armActionGate(host, 1, Crupier.PREFLOP);
                armActionGate(host, 3, Crupier.PREFLOP);
            }
            case "transport-chaos" -> {
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
                armActionGate(host, 2, Crupier.PREFLOP);
                armActionGate(nodes.get(3), 4, Crupier.PREFLOP);
            }
            case "lifecycle-chaos" -> {
                armActionGate(nodes.get(1), 1, Crupier.PREFLOP);
                armActionGate(host, 2, Crupier.PREFLOP);
                armActionGate(host, 3, Crupier.PREFLOP);
                armActionGate(nodes.get(2), 5, Crupier.PREFLOP);
                armActionGate(host, 6, Crupier.PREFLOP);
            }
            default -> {
                if (RealGameScenarioContract.isActionGated(scenario)) {
                    throw new IllegalStateException(
                            "action-gated scenario has no concrete gate plan: " + scenario);
                }
            }
        }
    }

    private static void armActionGate(NodeProcess node, int hand, int street)
            throws Exception {
        String gate = hand + "#" + street;
        node.send("ARM_ACTION_GATE#" + gate);
        assertTrue(node.await("CP_E2E_ACTION_GATE_ARMED gate=" + gate,
                Duration.ofSeconds(30)), node.diagnostic());
    }

    private static void awaitActionGate(NodeProcess node, int hand, int street)
            throws Exception {
        String gate = hand + "#" + street;
        assertTrue(node.await("CP_E2E_ACTION_GATE_REACHED gate=" + gate,
                Duration.ofMinutes(2)), node.diagnostic());
    }

    private static void releaseActionGate(NodeProcess node, int hand, int street)
            throws Exception {
        String gate = hand + "#" + street;
        node.send("RELEASE_ACTION_GATE#" + gate);
        assertTrue(node.await("CP_E2E_ACTION_GATE_RELEASED gate=" + gate,
                Duration.ofSeconds(30)), node.diagnostic());
    }

    private static void runAbruptExitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        NodeProcess victim = nodes.get(1);
        awaitActionGate(victim, 1, Crupier.PREFLOP);
        victim.killForcibly();

        assertMisdealRecovery(nodes, host, seats, 2);
    }

    private static void runDualAbruptExitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        awaitActionGate(nodes.get(1), 1, Crupier.PREFLOP);
        nodes.get(1).killForcibly();
        nodes.get(2).killForcibly();
        assertMisdealRecovery(nodes, host, seats, 3);
    }

    private static void runMixedExitCrashScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        NodeProcess testament = nodes.get(1);
        awaitActionGate(testament, 1, Crupier.PREFLOP);
        testament.send("CONTROLLED_EXIT");
        assertTrue(testament.await("CP_E2E_CONTROLLED_EXIT_SENT", Duration.ofSeconds(30)),
                testament.diagnostic());
        assertTrue(host.await("QA EXIT_TESTAMENT_ACCEPTED nick=client1",
                Duration.ofSeconds(30)), host.diagnostic());
        nodes.get(2).killForcibly();
        assertMisdealRecovery(nodes, host, seats, 3);
        assertFalse(testament.contains(
                "refusing to start betting without a verified honest-shuffle proof"),
                "controlled EXIT raced the client's shuffle-proof gate\n"
                + testament.diagnostic());
        assertFalse(testament.contains("ERROR UPDATING CARD IMAGE PRECACHE"),
                testament.diagnostic());
    }

    private static void assertMisdealRecovery(List<NodeProcess> nodes, NodeProcess host,
            int seats, int firstSurvivor) throws Exception {
        assertTrue(host.await("MISDEAL triggered:", Duration.ofMinutes(3)), host.diagnostic());
        assertTrue(host.awaitExpectedMisdeal("RECOVERY: abortAndRecover engaged",
                Duration.ofSeconds(30)),
                host.diagnostic());
        assertTrue(host.awaitExpectedMisdeal("CP_E2E_LEDGER", Duration.ofSeconds(30)),
                host.diagnostic());
        assertTrue(host.contains("potCents=0"), host.diagnostic());
        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        assertTrue(host.isAlive(), "host died after peer MISDEAL\n" + host.diagnostic());
        assertFalse(host.contains("TABLE_FAILURE_V1"), host.diagnostic());
        assertFalse(host.contains("CP_E2E_FAIL"), host.diagnostic());
        for (NodeProcess survivor : nodes.subList(firstSurvivor, nodes.size())) {
            assertTrue(survivor.awaitExpectedMisdeal("CP_E2E_RECOVERY_DIALOG_SUBMITTED",
                    Duration.ofMinutes(2)), survivor.diagnostic());
            assertFalse(survivor.contains("TABLE_FAILURE_V1"), survivor.diagnostic());
            assertFalse(survivor.contains("CP_E2E_FAIL"), survivor.diagnostic());
        }
    }

    private static void runControlledExitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        NodeProcess departingClient = nodes.get(1);
        awaitActionGate(departingClient, 1, Crupier.PREFLOP);

        departingClient.send("CONTROLLED_EXIT");
        assertTrue(departingClient.await("CP_E2E_CONTROLLED_EXIT_SENT", Duration.ofSeconds(30)),
                departingClient.diagnostic());
        assertTrue(host.await("QA EXIT_TESTAMENT_ACCEPTED nick=client1",
                Duration.ofSeconds(30)), host.diagnostic());
        assertTrue(departingClient.await("CP_E2E_EXPECTED_EXIT_COMPLETE",
                Duration.ofSeconds(30)), departingClient.diagnostic());
        assertFalse(departingClient.contains(
                "Cannot build mandatory all-in showdown proof"),
                departingClient.diagnostic());
        assertFalse(departingClient.contains("CP_E2E_FAIL"), departingClient.diagnostic());
        assertFalse(departingClient.contains("ERROR UPDATING CARD IMAGE PRECACHE"),
                "controlled EXIT left a card preload racing teardown\n"
                + departingClient.diagnostic());
        assertTrue(host.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(3)), host.diagnostic());
        assertTrue(host.await("CP_E2E_LEDGER", Duration.ofSeconds(30)), host.diagnostic());

        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        assertTrue(host.contains("verified: " + (nodes.size() - 1)
                + " receipts unanimous"), host.diagnostic());
        assertTrue(host.isAlive(), "host died after controlled EXIT\n" + host.diagnostic());
        assertFalse(host.contains("MISDEAL triggered:"), host.diagnostic());
        assertFalse(host.contains("TABLE_FAILURE_V1"), host.diagnostic());
        assertFalse(host.contains("CP_E2E_FAIL"), host.diagnostic());
        for (NodeProcess survivor : nodes.subList(2, nodes.size())) {
            assertTrue(survivor.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(3)),
                    survivor.diagnostic());
            assertEquals(host.linesContaining(" verified: "),
                    survivor.linesContaining(" verified: "),
                    "controlled-exit consensus divergence\n" + survivor.diagnostic());
            assertEquals(host.canonicalBalanceSnapshots(),
                    survivor.canonicalBalanceSnapshots(),
                    "controlled-exit balance divergence\n" + survivor.diagnostic());
            assertFalse(survivor.contains("TABLE_FAILURE_V1"), survivor.diagnostic());
            assertFalse(survivor.contains("CP_E2E_FAIL"), survivor.diagnostic());
        }
    }

    private static void runPauseResumeScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        awaitActionGate(host, 1, Crupier.PREFLOP);
        host.send("PAUSE_TOGGLE");
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_PAUSE_STATE paused=true", Duration.ofSeconds(30)),
                    node.diagnostic());
        }
        host.send("PAUSE_TOGGLE");
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_PAUSE_STATE paused=false", Duration.ofSeconds(30)),
                    node.diagnostic());
        }
        releaseActionGate(host, 1, Crupier.PREFLOP);
    }

    private static void runReconnectScenario(List<NodeProcess> nodes,
            NodeProcess host, int drops) throws Exception {
        for (int drop = 1; drop <= drops; drop++) {
            NodeProcess client = nodes.get(drop);
            String nick = "client" + drop;
            reconnectAtHand(client, host, nick, drop, 1);
        }
    }

    private static void runReconnectEveryStreetScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        NodeProcess client = nodes.get(1);
        int[] streets = {Crupier.PREFLOP, Crupier.FLOP, Crupier.TURN, Crupier.RIVER};
        for (int i = 0; i < streets.length; i++) {
            int hand = i + 1;
            awaitActionGate(client, hand, streets[i]);
            dropAndAwaitReconnect(client, host, "client1", hand);
            releaseActionGate(client, hand, streets[i]);
        }
    }

    private static void reconnectAtHand(NodeProcess client, NodeProcess host,
            String nick, int hand, long occurrence) throws Exception {
        awaitActionGate(client, hand, Crupier.PREFLOP);
        dropAndAwaitReconnect(client, host, nick, occurrence);
        releaseActionGate(client, hand, Crupier.PREFLOP);
    }

    private static void dropAndAwaitReconnect(NodeProcess client, NodeProcess host,
            String nick, long occurrence) throws Exception {
        client.send("DROP_SOCKET");
        awaitReconnectAfterDropRequested(client, host, nick, occurrence);
    }

    private static void awaitReconnectAfterDropRequested(NodeProcess client, NodeProcess host,
            String nick, long occurrence) throws Exception {
        awaitReconnectAttemptAfterDropRequested(client, occurrence);
        assertTrue(client.awaitCount("Connected to server! Exchanging keys", occurrence,
                Duration.ofMinutes(2)), client.diagnostic());
        assertTrue(host.awaitCount("Participant " + nick + " resetSocket OK", occurrence,
                Duration.ofMinutes(2)), host.diagnostic());
        assertFalse(client.contains("RECONNECT_DENIED"), client.diagnostic());
    }

    private static void awaitReconnectAttemptAfterDropRequested(NodeProcess client,
            long occurrence) throws Exception {
        assertTrue(client.awaitCount("CP_E2E_SOCKET_DROP_REQUESTED", occurrence,
                Duration.ofSeconds(30)), client.diagnostic());
        assertTrue(client.awaitCount("Attempting to reconnect to server", occurrence,
                Duration.ofMinutes(2)), client.diagnostic());
    }

    private static void runReconnectStormScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        NodeProcess first = nodes.get(1);
        awaitActionGate(first, 1, Crupier.PREFLOP);
        dropAndAwaitReconnect(first, host, "client1", 1);
        // Re-drop the freshly installed socket before the hand changes. This catches
        // stale reader ownership, duplicate reconnect admission and one-shot guards.
        dropAndAwaitReconnect(first, host, "client1", 2);
        releaseActionGate(first, 1, Crupier.PREFLOP);
        reconnectAtHand(nodes.get(2), host, "client2", 2, 1);
    }

    private static void runDualReconnectScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        runDualReconnectScenario(nodes, host, true);
    }

    private static void runDualReconnectScenario(List<NodeProcess> nodes,
            NodeProcess host, boolean releaseGate) throws Exception {
        NodeProcess first = nodes.get(1);
        NodeProcess second = nodes.get(2);
        awaitActionGate(first, 1, Crupier.PREFLOP);
        first.send("DROP_SOCKET");
        second.send("DROP_SOCKET");
        awaitReconnectAfterDropRequested(first, host, "client1", 1);
        awaitReconnectAfterDropRequested(second, host, "client2", 1);
        if (releaseGate) {
            releaseActionGate(first, 1, Crupier.PREFLOP);
        }
        assertFalse(first.contains("RECONNECT_DENIED"), first.diagnostic());
        assertFalse(second.contains("RECONNECT_DENIED"), second.diagnostic());
    }

    private static void runHostChannelFlapScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        List<NodeProcess> clients = nodes.subList(1, nodes.size());
        awaitActionGate(clients.get(0), 1, Crupier.PREFLOP);
        // A short host/network outage as observed by every client while the
        // server JVM remains alive: all old channels die together and every
        // peer must independently authenticate and install a fresh socket.
        for (NodeProcess client : clients) {
            client.send("DROP_SOCKET");
        }
        for (int i = 0; i < clients.size(); i++) {
            NodeProcess client = clients.get(i);
            String nick = "client" + (i + 1);
            awaitReconnectAfterDropRequested(client, host, nick, 1);
        }
        releaseActionGate(clients.get(0), 1, Crupier.PREFLOP);
    }

    private static void runStraddleNetworkCutScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        NodeProcess straddler = null;
        int clientNumber = -1;
        while (System.nanoTime() < deadline && straddler == null) {
            assertFalse(nodes.stream().anyMatch(NodeProcess::hasTerminalFailure),
                    "peer failed while waiting for a remote straddler\n" + host.diagnostic());
            for (int i = 1; i < nodes.size(); i++) {
                if (nodes.get(i).contains("CP_E2E_STRADDLE_ACCEPTED")) {
                    straddler = nodes.get(i);
                    clientNumber = i;
                    break;
                }
            }
            if (straddler == null) {
                Thread.sleep(25L);
            }
        }
        assertTrue(straddler != null,
                "no remote human became the production straddler\n" + host.diagnostic());
        String nick = "client" + clientNumber;
        assertTrue(host.await("QA STRADDLE_RESP_ACCEPTED nick=" + nick,
                Duration.ofSeconds(30)), host.diagnostic());
        straddler.send("DROP_SOCKET");
        awaitReconnectAfterDropRequested(straddler, host,
                nick, 1);
    }

    private static void runTransportChaosScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        runDualReconnectScenario(nodes, host, false);
        dropAndAwaitReconnect(nodes.get(1), host, "client1", 2);
        releaseActionGate(nodes.get(1), 1, Crupier.PREFLOP);

        awaitActionGate(host, 2, Crupier.PREFLOP);
        host.send("PAUSE_TOGGLE");
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_PAUSE_STATE paused=true", Duration.ofSeconds(30)),
                    node.diagnostic());
        }
        host.send("PAUSE_TOGGLE");
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_PAUSE_STATE paused=false", Duration.ofSeconds(30)),
                    node.diagnostic());
        }
        performForceRecoveryCycle(nodes, host, 2, 1);
        reconnectAtHand(nodes.get(3), host, "client3", 4, 1);
        assertRecoveredSession(nodes, host, seats, 5, List.of(3, 5));
        for (NodeProcess node : nodes) {
            assertFalse(node.contains("RECONNECT_DENIED"), node.diagnostic());
            assertFalse(node.contains("RECOVERDATA rejected"), node.diagnostic());
            assertFalse(node.contains("DECK_CASCADE_REQ received mid-hand"), node.diagnostic());
        }
    }

    private static void runReconnectForceRecoverScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        NodeProcess client = nodes.get(1);
        awaitActionGate(host, 1, Crupier.PREFLOP);
        // Prove that ordinary reconnect has actually started before asking the
        // host to force-recover. The reconnect may win or teardown may win;
        // both are legitimate scheduler interleavings. The invariant is that
        // every peer reaches the recovery lobby and the recovered game remains
        // cryptographically and financially identical.
        client.send("DROP_SOCKET");
        awaitReconnectAttemptAfterDropRequested(client, 1);
        host.send("FORCE_RECOVER");
        assertTrue(host.await("CP_E2E_FORCE_RECOVER_REQUESTED", Duration.ofSeconds(30)),
                host.diagnostic());
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_RECOVERY_DIALOG_SUBMITTED", Duration.ofMinutes(2)),
                    node.diagnostic());
        }
        releaseActionGate(host, 1, Crupier.PREFLOP);
        long requiredClientConnections = (nodes.size() - 1L) * 2L;
        assertTrue(host.awaitCount(" connected", requiredClientConnections,
                Duration.ofMinutes(2)), host.diagnostic());
        host.send("START_RECOVERED_GAME");
        assertTrue(host.await("CP_E2E_RECOVERED_GAME_START_REQUESTED",
                Duration.ofSeconds(30)), host.diagnostic());
        for (NodeProcess node : nodes) {
            assertTrue(node.await("ZERO-TRUST: starting recuperarDatosClavePartida",
                    Duration.ofMinutes(3)), node.diagnostic());
        }
        assertRecoveredSession(nodes, host, seats, 3, List.of(2, 3));
        for (NodeProcess node : nodes) {
            assertFalse(node.contains("RECOVERDATA rejected"), node.diagnostic());
            assertFalse(node.contains("stale PREV_H"), node.diagnostic());
        }
        assertFalse(client.contains("RECONNECT_DENIED"), client.diagnostic());
    }

    private static void runLifecycleChaosScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        reconnectAtHand(nodes.get(1), host, "client1", 1, 1);

        awaitActionGate(host, 2, Crupier.PREFLOP);
        host.send("PAUSE_TOGGLE");
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_PAUSE_STATE paused=true", Duration.ofSeconds(30)),
                    node.diagnostic());
        }
        host.send("PAUSE_TOGGLE");
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_PAUSE_STATE paused=false", Duration.ofSeconds(30)),
                    node.diagnostic());
        }
        releaseActionGate(host, 2, Crupier.PREFLOP);

        performForceRecoveryCycle(nodes, host, 3, 1);
        reconnectAtHand(nodes.get(2), host, "client2", 5, 1);
        performForceRecoveryCycle(nodes, host, 6, 2);

        assertRecoveredSession(nodes, host, seats, 7, List.of(4, 7));
        for (NodeProcess node : nodes) {
            assertFalse(node.contains("RECONNECT_DENIED"), node.diagnostic());
            assertFalse(node.contains("RECOVERDATA rejected"), node.diagnostic());
            assertFalse(node.contains("DECK_CASCADE_REQ received mid-hand"), node.diagnostic());
        }
    }

    private static void runAllInAbruptExitScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        NodeProcess victim = nodes.get(1);
        awaitActionGate(victim, 1, Crupier.PREFLOP);
        victim.send("ALLIN_THEN_CRASH");
        assertTrue(victim.await("CP_E2E_ORDERED_ALLIN_ACTION_CLICKED",
                Duration.ofSeconds(30)),
                victim.diagnostic());
        victim.awaitExit(Duration.ofSeconds(30));
        assertMisdealRecovery(nodes, host, seats, 2);
    }

    private static void assertNormalSession(List<NodeProcess> nodes, NodeProcess host,
            int hands) throws Exception {
        for (NodeProcess node : nodes) {
            Duration completionTimeout = Duration.ofSeconds(Math.max(240L, hands * 60L));
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", completionTimeout), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("QA dialog suppressed [Error"), node.diagnostic());
            assertFalse(node.contains("MISDEAL triggered:"), node.diagnostic());
            assertFalse(node.contains("invalid-sig flag"), node.diagnostic());
            assertFalse(node.contains("disputed_hands row inserted"), node.diagnostic());
            assertFalse(node.contains("Error parsing remote action"), node.diagnostic());
            assertFalse(node.contains("SYNTHESIZING FOLD"), node.diagnostic());
            assertFalse(node.contains("invalid atomic POTCARDS"), node.diagnostic());
            assertFalse(node.contains("missing mandatory"), node.diagnostic());
            assertFalse(node.contains("FAILED signature verify"), node.diagnostic());
            assertFalse(node.contains("host forging"), node.diagnostic());
            assertFalse(node.contains("Recover action MISMATCH"), node.diagnostic());
            assertFalse(node.hasUnexpectedClientWriteFailure(),
                    "session emitted an unarmed, repeated or late client write failure\n"
                    + node.diagnostic());
        }

        List<String> hostConsensus = host.linesContaining(" verified: ");
        List<String> hostBalances = host.canonicalBalanceSnapshots();
        assertEquals(hands, hostConsensus.size(), host.diagnostic());
        assertEquals(hands, hostBalances.size(), host.diagnostic());
        for (NodeProcess node : nodes.subList(1, nodes.size())) {
            assertEquals(hostConsensus, node.linesContaining(" verified: "),
                    "consensus divergence\n" + node.diagnostic());
            assertEquals(hostBalances, node.canonicalBalanceSnapshots(),
                    "balance divergence\n" + node.diagnostic());
        }
    }

    private static void runAllInRitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(4)),
                    node.diagnostic());
            assertTrue(node.contains("CP_E2E_RIT_VOTE decision=run-it-twice"),
                    node.diagnostic());
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("QA dialog suppressed [Error"), node.diagnostic());
        }
        assertTrue(host.contains("RUN-IT-TWICE vote result: true"), host.diagnostic());
        assertEquals(3, host.countContaining("Initiating SRA SIDE-B street unlock:"),
                host.diagnostic());
        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());

        List<String> hostConsensus = host.linesContaining(" verified: ");
        List<String> hostBalances = host.canonicalBalanceSnapshots();
        assertEquals(1, hostConsensus.size(), host.diagnostic());
        assertEquals(1, hostBalances.size(), host.diagnostic());
        for (NodeProcess node : nodes.subList(1, nodes.size())) {
            assertEquals(hostConsensus, node.linesContaining(" verified: "),
                    "RIT consensus divergence\n" + node.diagnostic());
            assertEquals(hostBalances, node.canonicalBalanceSnapshots(),
                    "RIT balance divergence\n" + node.diagnostic());
        }
    }

    private static void runRitNetworkCutScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        NodeProcess voter = nodes.get(1);
        NodeProcess delayedVoter = nodes.get(2);
        assertTrue(voter.await("CP_E2E_RIT_VOTE decision=run-it-twice",
                Duration.ofMinutes(3)), voter.diagnostic());
        assertTrue(delayedVoter.await("CP_E2E_RIT_VOTE_GATE_REACHED",
                Duration.ofSeconds(30)), delayedVoter.diagnostic());
        assertTrue(host.await("QA RIT_VOTE_ACCEPTED nick=client1",
                Duration.ofSeconds(30)), host.diagnostic());
        voter.send("DROP_SOCKET");
        awaitReconnectAfterDropRequested(voter, host, "client1", 1);
        delayedVoter.send("RELEASE_RIT_VOTE");
        assertTrue(delayedVoter.await("CP_E2E_RIT_VOTE_GATE_RELEASED",
                Duration.ofSeconds(30)), delayedVoter.diagnostic());
        runAllInRitScenario(nodes, host, seats);
        assertFalse(voter.contains("RIT_VOTE_CLOSE overrides"), voter.diagnostic());
        assertFalse(voter.contains("invalid atomic POTCARDS"), voter.diagnostic());
    }

    private static void runAllInReconnectScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        NodeProcess allInPeer = nodes.get(1);
        awaitActionGate(allInPeer, 1, Crupier.PREFLOP);
        allInPeer.send("ALLIN_THEN_DROP_SOCKET");
        assertTrue(allInPeer.await("CP_E2E_ORDERED_ALLIN_ACTION_CLICKED",
                Duration.ofSeconds(30)),
                allInPeer.diagnostic());
        awaitReconnectAfterDropRequested(allInPeer, host, "client1", 1);
        releaseActionGate(allInPeer, 1, Crupier.PREFLOP);
        assertNormalSession(nodes, host, 1);
        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        for (NodeProcess node : nodes) {
            assertFalse(node.contains("invalid atomic POTCARDS"), node.diagnostic());
            assertFalse(node.contains("missing mandatory"), node.diagnostic());
        }
    }

    private static void runAllInControlledExitScenario(List<NodeProcess> nodes,
            NodeProcess host) throws Exception {
        NodeProcess departingClient = nodes.get(1);
        awaitActionGate(departingClient, 1, Crupier.PREFLOP);
        departingClient.send("ALLIN_THEN_CONTROLLED_EXIT");
        assertTrue(departingClient.await("CP_E2E_ORDERED_ALLIN_ACTION_CLICKED",
                Duration.ofSeconds(30)), departingClient.diagnostic());
        assertTrue(departingClient.await("CP_E2E_CONTROLLED_EXIT_SENT", Duration.ofSeconds(30)),
                departingClient.diagnostic());
        assertTrue(host.await("QA EXIT_TESTAMENT_ACCEPTED nick=client1",
                Duration.ofSeconds(30)), host.diagnostic());
        assertTrue(departingClient.await("CP_E2E_EXPECTED_EXIT_COMPLETE",
                Duration.ofSeconds(30)), departingClient.diagnostic());
        assertTrue(host.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(4)),
                host.diagnostic());
        // HANDS_COMPLETE and LEDGER are consecutive writes from the node, but
        // the parent reader is asynchronous. Wait for the second marker instead
        // of racing the collector after observing only the first one.
        assertTrue(host.await("CP_E2E_LEDGER", Duration.ofSeconds(30)),
                host.diagnostic());
        assertTrue(host.contains("balanceRows=2"), host.diagnostic());
        assertTrue(host.contains("stackCents=2000"), host.diagnostic());
        assertTrue(host.contains("verified: 1 receipts unanimous"), host.diagnostic());
        assertTrue(host.isAlive(), "host died after all-in EXIT\n" + host.diagnostic());
        assertFalse(host.contains("MISDEAL triggered:"), host.diagnostic());
        assertFalse(host.contains("Cannot build mandatory all-in showdown proof"),
                host.diagnostic());
        assertFalse(host.contains("invalid atomic POTCARDS"), host.diagnostic());
        assertFalse(host.contains("missing mandatory"), host.diagnostic());
        assertFalse(host.contains("TABLE_FAILURE_V1"), host.diagnostic());
        assertFalse(host.contains("CP_E2E_FAIL"), host.diagnostic());
        assertFalse(host.contains("QA dialog suppressed [Error"), host.diagnostic());
        assertFalse(departingClient.contains("ERROR UPDATING CARD IMAGE PRECACHE"),
                "all-in EXIT left a card preload racing teardown\n"
                + departingClient.diagnostic());
        assertFalse(departingClient.contains("CP_E2E_FAIL"), departingClient.diagnostic());
    }

    private static void runForceRecoverScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats, int expectedHands)
            throws Exception {
        performForceRecoveryCycle(nodes, host, 1, 1);
        assertRecoveredSession(nodes, host, seats, expectedHands, List.of(2));
    }

    private static void runDoubleForceRecoverScenario(List<NodeProcess> nodes,
            NodeProcess host, int seats) throws Exception {
        performForceRecoveryCycle(nodes, host, 1, 1);
        performForceRecoveryCycle(nodes, host, 3, 2);
        assertRecoveredSession(nodes, host, seats, 4, List.of(2, 4));
    }

    private static void runCrashRejoinRecoverScenario(Path root,
            List<NodeProcess> nodes, NodeProcess host, int port, int clients,
            int bots, int hands, long seed, int seats) throws Exception {
        NodeProcess crashed = nodes.get(1);
        awaitActionGate(crashed, 1, Crupier.PREFLOP);
        crashed.killForcibly();

        assertTrue(host.await("MISDEAL triggered:", Duration.ofMinutes(3)),
                host.diagnostic());
        assertTrue(host.awaitExpectedMisdeal("RECOVERY: abortAndRecover engaged",
                Duration.ofSeconds(30)),
                host.diagnostic());
        assertTrue(host.awaitExpectedMisdeal("CP_E2E_RECOVERY_DIALOG_SUBMITTED",
                Duration.ofMinutes(2)),
                host.diagnostic());

        // Relaunch the exact same peer home: same SQLite game, nick and Ed25519
        // identity, like restarting CoronaPoker after a power cut.
        NodeProcess restarted = startNode(root.resolve("client-1"), "client", "client1",
                port, clients, bots, hands, seed + 1, true);
        nodes.set(1, restarted);
        assertTrue(restarted.await("CP_E2E_READY", Duration.ofSeconds(60)),
                restarted.diagnostic());
        assertTrue(restarted.await("identity loaded for nick=\"client1\"",
                Duration.ofSeconds(30)), restarted.diagnostic());
        assertTrue(host.awaitCount("client1 connected", 2, Duration.ofMinutes(2)),
                host.diagnostic());
        assertTrue(restarted.await("CP_E2E_LOBBY_READY", Duration.ofMinutes(2)),
                "restarted peer did not receive the complete human recovery roster\n"
                + restarted.diagnostic());

        host.send("START_RECOVERED_GAME");
        assertTrue(host.await("CP_E2E_RECOVERED_GAME_START_REQUESTED",
                Duration.ofSeconds(30)), host.diagnostic());
        assertTrue(host.await("ZERO-TRUST: starting recuperarDatosClavePartida",
                Duration.ofMinutes(3)), host.diagnostic());
        assertTrue(restarted.await("ZERO-TRUST: starting recuperarDatosClavePartida",
                Duration.ofMinutes(3)), restarted.diagnostic());

        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(5)),
                    node.diagnostic());
            assertTrue(node.contains("HAND 2: betting round Preflop"), node.diagnostic());
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("Recover action MISMATCH"), node.diagnostic());
            assertFalse(node.contains("FAILED signature verify"), node.diagnostic());
            assertFalse(node.contains("host forging"), node.diagnostic());
            assertFalse(node.contains("RECONNECT_DENIED"), node.diagnostic());
        }
        assertTrue(restarted.contains("SHUFFLE-VERIFY: deck verified OK (hand 2)"),
                restarted.diagnostic());
        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        assertEquals(host.linesContaining(" verified: "),
                restarted.linesContaining(" verified: "),
                "post-crash consensus divergence\n" + restarted.diagnostic());
        assertEquals(host.canonicalBalanceSnapshots(),
                restarted.canonicalBalanceSnapshots(),
                "post-crash balance divergence\n" + restarted.diagnostic());
    }

    private static void runForceRecoverAddClientScenario(Path root,
            List<NodeProcess> nodes, NodeProcess host, int port, int initialClients,
            int totalClients, int bots, int hands, long seed) throws Exception {
        awaitActionGate(host, 1, Crupier.PREFLOP);
        host.send("FORCE_RECOVER");
        assertTrue(host.await("CP_E2E_FORCE_RECOVER_REQUESTED", Duration.ofSeconds(30)),
                host.diagnostic());
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_RECOVERY_DIALOG_SUBMITTED", Duration.ofMinutes(2)),
                    node.diagnostic());
        }
        releaseActionGate(host, 1, Crupier.PREFLOP);

        List<NodeProcess> newcomers = new ArrayList<>();
        for (int i = initialClients + 1; i <= totalClients; i++) {
            NodeProcess newcomer = startNode(root.resolve("client-" + i), "client",
                    "client" + i,
                    // A fresh newcomer passively observes recovered hand 1 but does not
                    // persist it as a completed local hand. Its local target therefore
                    // starts with the first new hand it can actually play.
                    port, totalClients, bots, freshNewcomerTargetHands(hands), seed + i,
                    true);
            nodes.add(newcomer);
            newcomers.add(newcomer);
            assertTrue(newcomer.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    newcomer.diagnostic());
            assertTrue(host.await("client" + i + " connected", Duration.ofMinutes(2)),
                    host.diagnostic());
        }
        for (NodeProcess newcomer : newcomers) {
            assertTrue(newcomer.await("CP_E2E_LOBBY_READY", Duration.ofMinutes(2)),
                    "newcomer did not receive the complete human recovery roster\n"
                    + newcomer.diagnostic());
        }

        host.send("START_RECOVERED_GAME");
        assertTrue(host.await("CP_E2E_RECOVERED_GAME_START_REQUESTED",
                Duration.ofSeconds(30)), host.diagnostic());
        for (NodeProcess node : nodes) {
            assertTrue(node.await("ZERO-TRUST: starting recuperarDatosClavePartida",
                    Duration.ofMinutes(3)), node.diagnostic());
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(5)),
                    node.diagnostic());
            assertTrue(node.contains("HAND 2: betting round Preflop"), node.diagnostic());
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("Recover action MISMATCH"), node.diagnostic());
            assertFalse(node.contains("FAILED signature verify"), node.diagnostic());
            assertFalse(node.contains("host forging"), node.diagnostic());
            assertFalse(node.contains("invalid-sig flag"), node.diagnostic());
            assertFalse(node.contains("disputed_hands row inserted"), node.diagnostic());
            assertFalse(node.contains("Client write failed"),
                    "force-recover emitted a late client write on the retired socket\n"
                    + node.diagnostic());
        }

        for (NodeProcess newcomer : newcomers) {
            assertTrue(newcomer.contains("SHUFFLE-VERIFY: deck verified OK (hand 2)"),
                    newcomer.diagnostic());
        }
        assertTrue(host.contains("balanceRows=" + (totalClients + bots + 1)),
                host.diagnostic());
        assertTrue(host.contains("stackCents=" + ((totalClients + bots + 1) * 1000L)),
                host.diagnostic());
        List<String> hostBalances = host.canonicalBalanceSnapshots();
        List<String> hostConsensus = host.linesContaining(" verified: ");
        assertFalse(hostConsensus.isEmpty(), host.diagnostic());
        for (NodeProcess client : nodes.subList(1, nodes.size())) {
            List<String> clientBalances = client.canonicalBalanceSnapshots();
            List<String> clientConsensus = client.linesContaining(" verified: ");
            assertFalse(clientBalances.isEmpty(), client.diagnostic());
            assertFalse(clientConsensus.isEmpty(), client.diagnostic());
            assertEquals(hostBalances.get(hostBalances.size() - 1),
                    clientBalances.get(clientBalances.size() - 1),
                    "dynamic-roster balance divergence\n" + client.diagnostic());
            assertEquals(hostConsensus.get(hostConsensus.size() - 1),
                    clientConsensus.get(clientConsensus.size() - 1),
                    "dynamic-roster consensus divergence\n" + client.diagnostic());
        }
    }

    private static void runForceRecoverSwapClientScenario(Path root,
            List<NodeProcess> nodes, NodeProcess host, int port, int clients,
            int bots, int hands, long seed) throws Exception {
        awaitActionGate(host, 1, Crupier.PREFLOP);
        host.send("FORCE_RECOVER");
        assertTrue(host.await("CP_E2E_FORCE_RECOVER_REQUESTED", Duration.ofSeconds(30)),
                host.diagnostic());
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_RECOVERY_DIALOG_SUBMITTED", Duration.ofMinutes(2)),
                    node.diagnostic());
        }
        releaseActionGate(host, 1, Crupier.PREFLOP);

        NodeProcess missing = nodes.remove(1);
        missing.killForcibly();
        assertTrue(host.await("Participant client1 marked exit", Duration.ofMinutes(2)),
                host.diagnostic());

        NodeProcess newcomer = startNode(root.resolve("client-3"), "client", "client3",
                port, clients, bots, freshNewcomerTargetHands(hands), seed + 3, true);
        nodes.add(newcomer);
        assertTrue(newcomer.await("CP_E2E_READY", Duration.ofSeconds(60)),
                newcomer.diagnostic());
        assertTrue(host.await("client3 connected", Duration.ofMinutes(2)),
                host.diagnostic());
        assertTrue(newcomer.await("CP_E2E_LOBBY_READY", Duration.ofMinutes(2)),
                "replacement peer did not receive the complete human recovery roster\n"
                + newcomer.diagnostic());

        host.send("START_RECOVERED_GAME");
        assertTrue(host.await("CP_E2E_RECOVERED_GAME_START_REQUESTED",
                Duration.ofSeconds(30)), host.diagnostic());
        for (NodeProcess node : nodes) {
            assertTrue(node.await("ZERO-TRUST: starting recuperarDatosClavePartida",
                    Duration.ofMinutes(3)), node.diagnostic());
        }

        // The old hand cannot be replayed without client1's private material.
        // Recovery must atomically close/refund it and start hand 2 directly;
        // neither the survivor nor the newcomer may restore that stale crypto
        // context or be forced through a second reconnect cycle.
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(5)),
                    node.diagnostic());
            assertTrue(node.contains("HAND 2: betting round Preflop"), node.diagnostic());
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("Recover action MISMATCH"), node.diagnostic());
            assertFalse(node.contains("FAILED signature verify"), node.diagnostic());
            assertFalse(node.contains("host forging"), node.diagnostic());
            assertFalse(node.contains("invalid-sig flag"), node.diagnostic());
            assertFalse(node.contains("disputed_hands row inserted"), node.diagnostic());
            assertFalse(node.contains("Client write failed"),
                    "force-recover emitted a late client write on the retired socket\n"
                    + node.diagnostic());
        }
        assertTrue(host.contains("Recovery closed unreplayable hand"), host.diagnostic());
        assertEquals(1L, host.countContaining(
                "CP_E2E_RECOVERY_DIALOG_SUBMITTED role=production-dialog"),
                "swap recovery unexpectedly required another full recovery cycle\n"
                + host.diagnostic());
        assertTrue(newcomer.contains("SHUFFLE-VERIFY: deck verified OK (hand 2)"),
                newcomer.diagnostic());
        assertTrue(host.contains("balanceRows=" + (clients + bots + 2)),
                host.diagnostic());
        assertTrue(host.contains("stackCents=" + ((clients + bots + 2) * 1000L)),
                host.diagnostic());
        List<String> hostBalances = host.canonicalBalanceSnapshots();
        List<String> hostConsensus = host.linesContaining(" verified: ");
        assertFalse(hostConsensus.isEmpty(), host.diagnostic());
        String hostFinal = hostBalances.get(hostBalances.size() - 1);
        String hostFinalConsensus = hostConsensus.get(hostConsensus.size() - 1);
        for (NodeProcess client : nodes.subList(1, nodes.size())) {
            List<String> clientBalances = client.canonicalBalanceSnapshots();
            List<String> clientConsensus = client.linesContaining(" verified: ");
            assertFalse(clientConsensus.isEmpty(), client.diagnostic());
            assertEquals(hostFinal, clientBalances.get(clientBalances.size() - 1),
                    "replacement-roster balance divergence\n" + client.diagnostic());
            assertEquals(hostFinalConsensus,
                    clientConsensus.get(clientConsensus.size() - 1),
                    "replacement-roster consensus divergence\n" + client.diagnostic());
        }
    }

    private static void performForceRecoveryCycle(List<NodeProcess> nodes,
            NodeProcess host, int interruptedHand, long cycle) throws Exception {
        awaitActionGate(host, interruptedHand, Crupier.PREFLOP);
        host.send("FORCE_RECOVER");
        assertTrue(host.awaitCount("CP_E2E_FORCE_RECOVER_REQUESTED", cycle,
                Duration.ofSeconds(30)), host.diagnostic());

        for (NodeProcess node : nodes) {
            assertTrue(node.awaitCount("CP_E2E_RECOVERY_DIALOG_SUBMITTED", cycle,
                    Duration.ofMinutes(2)), node.diagnostic());
        }
        releaseActionGate(host, interruptedHand, Crupier.PREFLOP);
        long requiredClientConnections = (nodes.size() - 1L) * (cycle + 1L);
        assertTrue(host.awaitCount(" connected", requiredClientConnections,
                Duration.ofMinutes(2)), host.diagnostic());
        host.send("START_RECOVERED_GAME");
        assertTrue(host.awaitCount("CP_E2E_RECOVERED_GAME_START_REQUESTED", cycle,
                Duration.ofSeconds(30)), host.diagnostic());

        for (NodeProcess node : nodes) {
            assertTrue(node.awaitCount("ZERO-TRUST: starting recuperarDatosClavePartida", cycle,
                    Duration.ofMinutes(3)), node.diagnostic());
        }
    }

    private static void assertRecoveredSession(List<NodeProcess> nodes, NodeProcess host,
            int seats, int expectedHands, List<Integer> freshHands) throws Exception {
        Duration completionTimeout = Duration.ofSeconds(Math.max(240L, expectedHands * 60L));
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", completionTimeout),
                    node.diagnostic());
            for (int hand : freshHands) {
                assertTrue(node.contains("HAND " + hand + ": betting round Preflop"),
                        node.diagnostic());
            }
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("QA dialog suppressed [Error"), node.diagnostic());
            assertFalse(node.contains("Recover action MISMATCH"), node.diagnostic());
            assertFalse(node.contains("FAILED signature verify"), node.diagnostic());
            assertFalse(node.contains("host forging"), node.diagnostic());
            assertFalse(node.contains("invalid-sig flag"), node.diagnostic());
            assertFalse(node.contains("disputed_hands row inserted"), node.diagnostic());
            assertFalse(node.contains("Client write failed"),
                    "force-recover emitted a late client write on the retired socket\n"
                    + node.diagnostic());
        }

        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        assertTrue(host.isAlive(), "host died after force-recover\n" + host.diagnostic());
        for (NodeProcess client : nodes.subList(1, nodes.size())) {
            for (int hand : freshHands) {
                assertTrue(client.contains("SHUFFLE-VERIFY: deck verified OK (hand "
                        + hand + ")"), client.diagnostic());
            }
        }
        List<String> hostConsensus = host.linesContaining(" verified: ");
        List<String> hostBalances = host.canonicalBalanceSnapshots();
        assertEquals(expectedHands, hostConsensus.size(), host.diagnostic());
        assertEquals(expectedHands, hostBalances.size(), host.diagnostic());
        for (NodeProcess node : nodes.subList(1, nodes.size())) {
            assertEquals(hostConsensus, node.linesContaining(" verified: "),
                    "recovery consensus divergence\n" + node.diagnostic());
            assertEquals(hostBalances, node.canonicalBalanceSnapshots(),
                    "recovery balance divergence\n" + node.diagnostic());
        }
    }

    static int freshNewcomerTargetHands(int globalTargetHands) {
        if (globalTargetHands < 2) {
            throw new IllegalArgumentException(
                    "dynamic recovery newcomer requires a post-recovery hand");
        }
        return globalTargetHands - 1;
    }

    static long expectedMisdealDialogAllowance(long previousAllowance,
            long observedMisdeals, long observedMisdealDialogs) {
        return Math.max(previousAllowance,
                Math.max(observedMisdeals, observedMisdealDialogs));
    }

    private static NodeProcess startNode(Path home, String role, String nick, int port,
            int clients, int bots, int hands, long seed) throws IOException {
        return startNode(home, role, nick, port, clients, bots, hands, seed, false);
    }

    private static NodeProcess startNode(Path home, String role, String nick, int port,
            int clients, int bots, int hands, long seed,
            boolean lateRecoveryJoiner) throws IOException {
        Files.createDirectories(home);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                java,
                "-Djava.awt.headless=false",
                "-Dcoronapoker.qa.suppressDialogs=true",
                "-Dcoronapoker.qa.windowMode="
                        + System.getProperty("qa.e2e.windowMode", "hidden"),
                "-Dcoronapoker.qa.screen="
                        + Integer.getInteger("qa.e2e.screen", 2),
                "-Dcoronapoker.qa.animations="
                        + Boolean.getBoolean("qa.e2e.animations"),
                "-Dcoronapoker.qa.deterministicCrypto="
                        + Boolean.getBoolean("qa.e2e.deterministicCrypto"),
                "-Dcoronapoker.qa.scenario="
                        + System.getProperty("qa.e2e.scenario", "normal"),
                "-Dcoronapoker.testMode="
                        + System.getProperty("qa.e2e.testMode", "true"),
                "-Duser.home=" + home.toAbsolutePath(),
                "-cp", classpath,
                RealGameNodeMain.class.getName(),
                role, nick, Integer.toString(port), Integer.toString(clients),
                Integer.toString(bots), Integer.toString(hands), Long.toString(seed),
                Boolean.toString(lateRecoveryJoiner));
        builder.redirectErrorStream(true);
        return new NodeProcess(role + ":" + nick, builder.start());
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        int value = Integer.getInteger(name, fallback);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + "," + max + "]");
        }
        return value;
    }

    private static final class NodeProcess implements AutoCloseable {

        private final String name;
        private final Process process;
        private final List<String> output = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch readerDone = new CountDownLatch(1);
        private volatile long allowedExpectedMisdealDialogs;

        private NodeProcess(String name, Process process) {
            this.name = name;
            this.process = process;
            Thread reader = new Thread(() -> {
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        output.add(line);
                        // Keep the real peer trace visible while the watchdog is
                        // running. Otherwise a hang hides the decisive last line
                        // until the full JUnit timeout expires.
                        System.out.println("[" + name + "] " + line);
                    }
                } catch (IOException ex) {
                    output.add("reader failure: " + ex);
                } finally {
                    readerDone.countDown();
                }
            }, "e2e-output-" + name);
            reader.setDaemon(true);
            reader.start();
        }

        private boolean await(String marker, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (contains(marker)) {
                    return true;
                }
                if (hasTerminalFailure()) {
                    return false;
                }
                if (!process.isAlive()) {
                    readerDone.await(2, TimeUnit.SECONDS);
                    return contains(marker);
                }
                Thread.sleep(25L);
            }
            return contains(marker);
        }

        private boolean awaitCount(String marker, long expected, Duration timeout)
                throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (countContaining(marker) >= expected) {
                    return true;
                }
                if (hasTerminalFailure()) {
                    return false;
                }
                if (!process.isAlive()) {
                    readerDone.await(2, TimeUnit.SECONDS);
                    return countContaining(marker) >= expected;
                }
                Thread.sleep(25L);
            }
            return countContaining(marker) >= expected;
        }

        /**
         * Waits through the one error dialog that is the expected public outcome
         * of an abrupt peer loss without a testament. All other terminal signals
         * remain fail-fast. This is deliberately not a generic "ignore errors"
         * switch: callers must first have observed the production MISDEAL path.
         */
        private boolean awaitExpectedMisdeal(String marker, Duration timeout)
                throws InterruptedException {
            if (!contains("MISDEAL triggered:")
                    && !contains("MANO ANULADA")) {
                throw new IllegalStateException(
                        "expected-MISDEAL wait used before observing the MISDEAL path");
            }
            // Scope the exception to exactly one suppressed error dialog per
            // MISDEAL already observed. It remains active for the rest of this
            // node's recovery flow, while any additional error dialog still
            // fails immediately.
            allowedExpectedMisdealDialogs = expectedMisdealDialogAllowance(
                    allowedExpectedMisdealDialogs,
                    countContaining("MISDEAL triggered:"),
                    countContaining("QA dialog suppressed [Error]: MANO ANULADA"));
            return await(marker, timeout);
        }

        private boolean hasTerminalFailure() {
            return hasTerminalFailureExceptExpectedMisdeal()
                    || countContaining("QA dialog suppressed [Error")
                    > allowedExpectedMisdealDialogs;
        }

        private boolean hasTerminalFailureExceptExpectedMisdeal() {
            return contains("CP_E2E_FAIL")
                    || contains("TABLE_FAILURE_V1")
                    || contains("Empty settlement table; refusing receipt and SQL close")
                    || contains("Next-hand balance barrier disagrees with atomic opening rows")
                    || contains("Error parsing remote action")
                    || contains("SYNTHESIZING FOLD")
                    || contains("invalid atomic POTCARDS")
                    || contains("Cannot build mandatory all-in showdown proof")
                    || contains("missing mandatory")
                    || contains("FAILED signature verify")
                    || contains("host forging")
                    || contains("invalid-sig flag")
                    || contains("disputed_hands row inserted")
                    || contains("Recover action MISMATCH")
                    || contains("RECOVERDATA rejected")
                    || contains("DECK_CASCADE_REQ received mid-hand")
                    || hasUnexpectedClientWriteFailure()
                    || contains("RECONNECT_DENIED");
        }

        private boolean hasUnexpectedClientWriteFailure() {
            synchronized (output) {
                return RealGameScenarioContract.hasUnexpectedClientWriteFailure(output);
            }
        }

        private boolean contains(String text) {
            synchronized (output) {
                return output.stream().anyMatch(line -> line.contains(text));
            }
        }

        private List<String> linesContaining(String token) {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains(token))
                        .map(line -> line.substring(line.indexOf("Hand ")))
                        .toList();
            }
        }

        private long countContaining(String token) {
            synchronized (output) {
                return output.stream().filter(line -> line.contains(token)).count();
            }
        }

        private long countContainingBoth(String first, String second) {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains(first) && line.contains(second))
                        .count();
            }
        }

        private boolean hasBalanceWithBuyinAbove(int threshold) {
            return canonicalBalanceSnapshots().stream().skip(1).anyMatch(snapshot -> {
                int arrow = snapshot.indexOf(" -> ");
                if (arrow < 0) {
                    return false;
                }
                for (String balance : snapshot.substring(arrow + 4).split("@")) {
                    String[] fields = balance.split("\\|");
                    if (fields.length >= 3 && Integer.parseInt(fields[2]) > threshold) {
                        return true;
                    }
                }
                return false;
            });
        }

        private List<String> canonicalBalanceSnapshots() {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains("Balance after hand "))
                        .map(line -> line.substring(line.indexOf("Balance after hand ")))
                        .map(line -> {
                            int arrow = line.indexOf(" -> ");
                            String prefix = line.substring(0, arrow + 4);
                            String[] balances = line.substring(arrow + 4).split("@");
                            java.util.Arrays.sort(balances);
                            return prefix + String.join("@", balances);
                        })
                        .toList();
            }
        }

        private String diagnostic() {
            synchronized (output) {
                int start = Math.max(0, output.size() - 120);
                return "Node " + name + " (alive=" + process.isAlive() + ") output:\n"
                        + String.join("\n", output.subList(start, output.size()));
            }
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private void send(String command) throws IOException {
            process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }

        private void killForcibly() throws InterruptedException {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS),
                    "node did not terminate after forced crash\n" + diagnostic());
            readerDone.await(2, TimeUnit.SECONDS);
            assertFalse(process.isAlive(),
                    "node remained alive after forced crash\n" + diagnostic());
        }

        private int intValueAfter(String marker, String field) {
            synchronized (output) {
                for (String line : output) {
                    int markerAt = line.indexOf(marker);
                    int fieldAt = markerAt < 0 ? -1 : line.indexOf(field, markerAt);
                    if (fieldAt >= 0) {
                        int start = fieldAt + field.length();
                        int end = start;
                        while (end < line.length() && Character.isDigit(line.charAt(end))) {
                            end++;
                        }
                        if (end > start) {
                            return Integer.parseInt(line.substring(start, end));
                        }
                    }
                }
            }
            return -1;
        }

        private void awaitExit(Duration timeout) throws InterruptedException {
            assertTrue(process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS),
                    "node did not exit within " + timeout + "\n" + diagnostic());
            readerDone.await(2, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            try {
                if (process.isAlive()) {
                    try {
                        send("STOP");
                    } catch (IOException ignored) {
                        // The node may already have terminated after a fault.
                    }
                }
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
