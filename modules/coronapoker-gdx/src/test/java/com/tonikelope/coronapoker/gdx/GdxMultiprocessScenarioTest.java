package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Multiprocess GDX port of the historical Swing scenario harness.
 *
 * <p>The Swing scenarios are the GOLD behavioural specification. Existing
 * in-process GDX tests may supply GDX controls and observation helpers, but
 * they never replace or weaken the Swing topology, disruption sequence or
 * assertions represented here.</p>
 */
@Tag("certification")
class GdxMultiprocessScenarioTest {

    private static final Set<String> EXPECTED_MISDEAL_SCENARIOS = Set.of(
            "abrupt-exit", "dual-abrupt-exit", "mixed-exit-crash",
            "allin-abrupt-exit", "crash-rejoin-recover",
            "spectator-double-recovery-crash-mix");

    /** Exact terminal MISDEAL set frozen by the final Swing GOLD contract. */
    private static final Set<String> TERMINAL_MISDEAL_SCENARIOS = Set.of(
            "abrupt-exit", "dual-abrupt-exit", "mixed-exit-crash",
            "allin-abrupt-exit");

    private static final List<String> ALWAYS_FATAL_OUTPUT = List.of(
            "CP_GDX_E2E_FAIL",
            "TABLE_FAILURE_V1",
            "LA TIMBA HA TERMINADO (NO QUEDAN JUGADORES)",
            "GAME OVER (NO PLAYERS LEFT)",
            "Empty settlement table; refusing receipt and SQL close",
            "Next-hand balance barrier disagrees with atomic opening rows",
            "Error parsing remote action",
            "SYNTHESIZING FOLD",
            "invalid atomic POTCARDS",
            "Cannot build mandatory all-in showdown proof",
            "missing mandatory",
            "FAILED signature verify",
            "host forging",
            "disputed_hands row inserted",
            "invalid-sig flag",
            "Recover action MISMATCH",
            "RECOVERDATA rejected",
            "stale PREV_H",
            "DECK_CASCADE_REQ received mid-hand",
            "RIT_VOTE_CLOSE overrides",
            "refusing to start betting without a verified honest-shuffle proof");

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void normalSupportingTopologyRunsAsIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "normal", 1, 2, null);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void liveHotJoinAfterBotOnlyStartWarmsThenPlaysTheNextHand(
            @TempDir Path root) throws Exception {
        runLiveHotJoinScenario(root, 0, 1);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void liveHotJoinAfterHumanOnlyStartWarmsThenPlaysTheNextHand(
            @TempDir Path root) throws Exception {
        runLiveHotJoinScenario(root, 1, 0);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void liveHotJoinAfterMixedStartWarmsThenPlaysTheNextHand(
            @TempDir Path root) throws Exception {
        runLiveHotJoinScenario(root, 1, 1);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void freshHotJoinOnFlopReceivesCompletePublicBootstrap(
            @TempDir Path root) throws Exception {
        runLiveHotJoinScenario(root, 0, 1,
                "live-hot-join-flop-bootstrap");
    }

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void twoConcurrentHotJoinsWarmAndEnterTheSameNextHand(
            @TempDir Path root) throws Exception {
        runTwoConcurrentHotJoinsScenario(root);
    }

    @Test
    @Timeout(360)
    void oneOfTwoConcurrentHotJoinsCanLeaveWhileTheOtherIsAdmitted(
            @TempDir Path root) throws Exception {
        runConcurrentHotJoinExitScenario(root);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void warmingHotJoinReceivesHostTableStop(@TempDir Path root)
            throws Exception {
        runWarmingHotJoinStopScenario(root);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void warmingHotJoinCanLeaveWithoutEndingTheRunningTable(
            @TempDir Path root) throws Exception {
        runWarmingHotJoinExitScenario(root);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void warmingHotJoinCanLeaveWhileBootstrapFramesAreInFlight(
            @TempDir Path root) throws Exception {
        runWarmingHotJoinBootstrapExitScenario(root);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void newlyAdmittedHotJoinCanLeaveAtTheHandBoundary(
            @TempDir Path root) throws Exception {
        runNewlyAdmittedHotJoinExitScenario(root);
    }

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void warmingHotJoinCanLeaveAndReenterWithSameIdentityAndStack(
            @TempDir Path root) throws Exception {
        runWarmingHotJoinReentryScenario(root, false);
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void warmingHotJoinCanLeaveAndReenterInALaterHandWithSameIdentityAndStack(
            @TempDir Path root) throws Exception {
        runWarmingHotJoinReentryScenario(root, true);
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void warmingHotJoinCanCrashAndReenterWithSameIdentityAndStack(
            @TempDir Path root) throws Exception {
        runWarmingHotJoinCrashReentryScenario(root);
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void activePlayerCanLeaveMidDecisionAndReenterAsWarmingOwner(
            @TempDir Path root) throws Exception {
        runActivePlayerHotReentryScenario(root);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void admittedHotJoinCanLeaveLaterAndReenterAsWarmingOwner(
            @TempDir Path root) throws Exception {
        runAdmittedHotJoinActiveReentryScenario(root);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void normalHeadsUpMatchesTheSwingGoldTopologyAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "normal", 1, 0, null,
                configuredHands("coronapoker.qa.gdx.headsUpHands", 5));
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void normalSoakMatchesTheSwingGoldTopologyAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "normal", 2, 2, null,
                configuredHands("coronapoker.qa.gdx.soakHands", 5));
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void normalFullMixedMatchesTheSwingGoldTopologyAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "normal", 4, 5, null,
                configuredHands("coronapoker.qa.gdx.fullMixedHands", 1));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void normalFullHumanMatchesTheSwingGoldTopologyAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "normal", 9, 0, null,
                configuredHands("coronapoker.qa.gdx.fullHumanHands", 1));
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void raiseMixMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "raise-mix", 2, 2, null, 10);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void allInSingleBoardMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "allin-single-board", 1, 0, null, 1);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void allInRebuyMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "allin-rebuy", 1, 0, null, 5);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void allInRunItTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "allin-rit", 1, 0, null, 1);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void runItTwiceVoteSurvivesNetworkCutAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "rit-network-cut", 2, 0, null, 1);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void straddlePostMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "straddle-post", 2, 0, null, 3);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void straddleAcceptedResponseSurvivesNetworkCutAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "straddle-network-cut", 2, 0, null, 3);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void controlledExitUsesIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "controlled-exit", 2, 1,
                "CONTROLLED_EXIT", 2);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void abruptProcessExitLeavesIndependentGdxPeersRecoverable(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "abrupt-exit", 2, 1,
                "CRASH_PROCESS");
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void dualAbruptExitMatchesTheSwingGoldScenarioAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "dual-abrupt-exit", 3, 1, null);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void mixedControlledExitAndCrashMatchesTheSwingGoldScenario(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "mixed-exit-crash", 3, 1, null);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void crashRejoinRecoveryRestartsTheSameGdxPeerAndCompletesHandTwo(
            @TempDir Path root) throws Exception {
        runCrashRejoinRecoverScenario(root);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void forceRecoveryRebuildsBothGdxProcessesAndCompletesTwoHands(
            @TempDir Path root) throws Exception {
        runForceRecoverScenario(root);
    }

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void doubleForceRecoveryRebuildsBothGdxProcessesAtHandsOneAndThree(
            @TempDir Path root) throws Exception {
        runDoubleForceRecoverScenario(root);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void reconnectAndForceRecoveryConvergeAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runReconnectForceRecoverScenario(root);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void forceRecoveryAdmitsNewIndependentGdxProcessForFreshSecondHand(
            @TempDir Path root) throws Exception {
        runForceRecoverAddClientScenario(root);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void forceRecoveryAdmitsTwoNewIndependentGdxProcessesForSecondHand(
            @TempDir Path root) throws Exception {
        runForceRecoverAddTwoScenario(root);
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void forceRecoveryReplacesMissingIndependentGdxProcessForSecondHand(
            @TempDir Path root) throws Exception {
        runForceRecoverSwapClientScenario(root);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void pauseResumePreservesTheDecisionAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "pause-resume", 2, 1, null, 2);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void reconnectMidHandReplacesTheSocketAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "reconnect-midhand", 2, 1, null, 2);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void reconnectTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "reconnect-twice", 2, 1, null, 3);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void reconnectEveryStreetMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "reconnect-every-street", 2, 1, null, 4);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void reconnectStormMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "reconnect-storm", 2, 1, null, 4);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void dualReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "dual-reconnect", 3, 1, null, 3);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void hostChannelFlapMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "host-channel-flap", 3, 1, null, 2);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void allInReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "allin-reconnect", 2, 0, null, 1);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void allInControlledExitMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "allin-controlled-exit", 2, 1, null, 2);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void allInAbruptExitMatchesTheSwingGoldSequenceAcrossGdxProcesses(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "allin-abrupt-exit", 2, 0, null, 1);
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void spectatorRebuyCycleReturnsBustedIndependentGdxProcessToPlay(
            @TempDir Path root) throws Exception {
        runCompletingScenario(root, "spectator-rebuy-cycle", 3, 0, null, 7);
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void spectatorsSurviveRecoveryRebuyAndTwoNewIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runSpectatorRecoveryMixScenario(root);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void bustedBotRegrowsAcrossIndependentGdxProcessesAfterRecovery(
            @TempDir Path root) throws Exception {
        runBotBustRecoveryScenario(root, true);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void bustedBotDropsAcrossIndependentGdxProcessesAfterRecovery(
            @TempDir Path root) throws Exception {
        runBotBustRecoveryScenario(root, false);
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void bustedHumanExitsRejoinsWithSameIdentityAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runHumanBustExitRejoinRebuyScenario(root);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void spectatorDoubleRecoveryCrashMixRunsAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runSpectatorDoubleRecoveryCrashMixScenario(root);
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void transportChaosConvergesAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runTransportChaosScenario(root);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void lifecycleChaosConvergesAcrossIndependentGdxProcesses(
            @TempDir Path root) throws Exception {
        runLifecycleChaosScenario(root);
    }

    private static void runCompletingScenario(Path root, String scenario,
            int clients, int bots, String disruption) throws Exception {
        runCompletingScenario(root, scenario, clients, bots, disruption, 1);
    }

    private static int configuredHands(String property, int fallback) {
        String configured = System.getProperty(property);
        if (configured == null || configured.isBlank()) return fallback;
        int hands = Integer.parseInt(configured);
        if (hands < 1 || hands > 1_000) {
            throw new IllegalArgumentException(property + " must be 1..1000");
        }
        return hands;
    }

    private static Duration completionTimeout(int hands, int participants) {
        // The certification profiles deliberately scale this scenario from
        // 5 hands (fast) to 20 (balanced) and 50 (stress), and from heads-up
        // to a full ten-seat table. Keep a firm bound, but account for both
        // dimensions instead of timing out a healthy full table near the end
        // of its final hand.
        long handBudget = 60L + (15L * hands);
        long tableBudget = 60L + ((5L + (2L * participants)) * hands);
        return Duration.ofSeconds(Math.max(180L,
                Math.max(handBudget, tableBudget)));
    }

    private static void runCompletingScenario(Path root, String scenario,
            int clients, int bots, String disruption, int hands)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        Set<NodeProcess> killed = new HashSet<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, clients, bots, hands, scenario);
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (int index = 1; index <= clients; index++) {
                NodeProcess client = startNode(root.resolve("client-" + index),
                        "client", "client" + index, port, clients, bots, hands,
                        scenario);
                nodes.add(client);
                assertTrue(client.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), client.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY",
                        Duration.ofSeconds(30)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED",
                    Duration.ofSeconds(20)), host.diagnostic());
            if (scenario.equals("allin-controlled-exit")
                    || scenario.equals("allin-abrupt-exit")) {
                NodeProcess allInPeer = nodes.get(1);
                assertTrue(allInPeer.await(
                        "CP_GDX_E2E_ACTION_GATE_REACHED scenario=" + scenario
                        + " nick=client1 hand=1", Duration.ofSeconds(60)),
                        allInPeer.diagnostic());
                allInPeer.send(scenario.equals("allin-controlled-exit")
                        ? "ALLIN_THEN_CONTROLLED_EXIT" : "ALLIN_THEN_CRASH");
                assertTrue(allInPeer.await(
                        "CP_GDX_E2E_ORDERED_ALLIN_ACTION_CLICKED"
                        + " nick=client1 hand=1", Duration.ofSeconds(30)),
                        allInPeer.diagnostic());
                if (scenario.equals("allin-controlled-exit")) {
                    assertTrue(allInPeer.await(
                            "CP_GDX_E2E_CONTROLLED_EXIT_SENT"
                            + " nick=client1 after=all-in",
                            Duration.ofSeconds(30)), allInPeer.diagnostic());
                    assertTrue(host.await(
                            "QA EXIT_TESTAMENT_ACCEPTED nick=client1",
                            Duration.ofSeconds(30)), host.diagnostic());
                } else {
                    assertTrue(allInPeer.awaitExit(Duration.ofSeconds(30)) != 0,
                            allInPeer.diagnostic());
                    killed.add(allInPeer);
                }
            } else if (scenario.equals("straddle-network-cut")) {
                NodeProcess straddler = null;
                int straddlerIndex = -1;
                long deadline = System.nanoTime()
                        + Duration.ofSeconds(90).toNanos();
                while (straddler == null && System.nanoTime() < deadline) {
                    for (int index = 1; index < nodes.size(); index++) {
                        if (nodes.get(index).contains(
                                "CP_GDX_E2E_STRADDLE_ACCEPTED")) {
                            straddler = nodes.get(index);
                            straddlerIndex = index;
                            break;
                        }
                    }
                    if (straddler == null) {
                        Thread.sleep(10L);
                    }
                }
                assertTrue(straddler != null,
                        "no remote GDX straddler accepted\n"
                        + host.diagnostic());
                String nickname = "client" + straddlerIndex;
                assertTrue(host.await(
                        "QA STRADDLE_RESP_ACCEPTED nick=" + nickname,
                        Duration.ofSeconds(30)), host.diagnostic());
                for (int index = 1; index < nodes.size(); index++) {
                    nodes.get(index).send(index == straddlerIndex
                            ? "DROP_SOCKET" : "NO_DROP");
                }
                assertTrue(straddler.await(
                        "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=" + nickname
                        + " phase=straddle", Duration.ofSeconds(30)),
                        straddler.diagnostic());
                assertTrue(straddler.await(
                        "CP_GDX_E2E_RECONNECTED peer=server count=1"
                        + " phase=straddle", Duration.ofSeconds(90)),
                        straddler.diagnostic());
                assertTrue(host.await(
                        "CP_GDX_E2E_RECONNECTED peer=" + nickname
                        + " count=1 phase=straddle",
                        Duration.ofSeconds(90)), host.diagnostic());
            } else if (scenario.equals("rit-network-cut")) {
                NodeProcess voter = nodes.get(1);
                NodeProcess delayedVoter = nodes.get(2);
                assertTrue(voter.await(
                        "CP_GDX_E2E_RIT_VOTE_ACCEPTED nick=client1",
                        Duration.ofSeconds(90)), voter.diagnostic());
                assertTrue(delayedVoter.await(
                        "CP_GDX_E2E_RIT_VOTE_GATE_REACHED nick=client2 votes=2",
                        Duration.ofSeconds(90)), delayedVoter.diagnostic());
                voter.send("DROP_SOCKET");
                assertTrue(voter.await(
                        "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client1"
                        + " phase=rit-vote", Duration.ofSeconds(30)),
                        voter.diagnostic());
                assertTrue(voter.await(
                        "CP_GDX_E2E_RECONNECTED peer=server count=1"
                        + " phase=rit-vote", Duration.ofSeconds(90)),
                        voter.diagnostic());
                assertTrue(host.await(
                        "CP_GDX_E2E_RECONNECTED peer=client1 count=1"
                        + " phase=rit-vote", Duration.ofSeconds(90)),
                        host.diagnostic());
                delayedVoter.send("RELEASE_RIT_VOTE");
                assertTrue(delayedVoter.await(
                        "CP_GDX_E2E_RIT_VOTE_GATE_RELEASED nick=client2",
                        Duration.ofSeconds(30)), delayedVoter.diagnostic());
            } else if (scenario.equals("allin-reconnect")) {
                NodeProcess allInPeer = nodes.get(1);
                assertTrue(allInPeer.await(
                        "CP_GDX_E2E_ACTION_GATE_REACHED"
                        + " scenario=allin-reconnect nick=client1 hand=1",
                        Duration.ofSeconds(60)), allInPeer.diagnostic());
                allInPeer.send("ALLIN_THEN_DROP_SOCKET");
                assertTrue(allInPeer.await(
                        "CP_GDX_E2E_ORDERED_ALLIN_ACTION_CLICKED"
                        + " nick=client1 hand=1", Duration.ofSeconds(30)),
                        allInPeer.diagnostic());
                assertTrue(allInPeer.await(
                        "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client1"
                        + " hand=1 after=all-in", Duration.ofSeconds(30)),
                        allInPeer.diagnostic());
                assertTrue(allInPeer.await(
                        "CP_GDX_E2E_RECONNECTED peer=server count=1"
                        + " hand=1 after=all-in", Duration.ofSeconds(90)),
                        allInPeer.diagnostic());
                assertTrue(host.await(
                        "CP_GDX_E2E_RECONNECTED peer=client1 count=1"
                        + " hand=1 after=all-in", Duration.ofSeconds(90)),
                        host.diagnostic());
            } else if (scenario.equals("dual-reconnect")
                    || scenario.equals("host-channel-flap")) {
                NodeProcess first = nodes.get(1);
                assertTrue(first.await(
                        "CP_GDX_E2E_ACTION_GATE_REACHED scenario=" + scenario
                        + " nick=client1 hand=1", Duration.ofSeconds(60)),
                        first.diagnostic());
                int reconnectingClients = scenario.equals("host-channel-flap")
                        ? clients : 2;
                for (int index = 1; index <= reconnectingClients; index++) {
                    nodes.get(index).send("DROP_SOCKET");
                }
                for (int index = 1; index <= reconnectingClients; index++) {
                    NodeProcess reconnecting = nodes.get(index);
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client"
                            + index + " hand=1 count=1",
                            Duration.ofSeconds(30)), reconnecting.diagnostic());
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_RECONNECTED peer=server count=1"
                            + " hand=1 nick=client" + index,
                            Duration.ofSeconds(90)), reconnecting.diagnostic());
                    assertTrue(host.await(
                            "CP_GDX_E2E_RECONNECTED peer=client" + index
                            + " count=1 hand=1", Duration.ofSeconds(90)),
                            host.diagnostic());
                    assertTrue(!reconnecting.contains("RECONNECT_DENIED"),
                            reconnecting.diagnostic());
                }
            } else if (scenario.equals("reconnect-storm")) {
                NodeProcess first = nodes.get(1);
                NodeProcess second = nodes.get(2);
                assertTrue(first.await(
                        "CP_GDX_E2E_ACTION_GATE_REACHED scenario=" + scenario
                        + " nick=client1 hand=1", Duration.ofSeconds(60)),
                        first.diagnostic());
                for (int occurrence = 1; occurrence <= 2; occurrence++) {
                    first.send("DROP_SOCKET");
                    assertTrue(first.await(
                            "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client1"
                            + " hand=1 count=" + occurrence,
                            Duration.ofSeconds(30)), first.diagnostic());
                    assertTrue(first.await(
                            "CP_GDX_E2E_RECONNECTED peer=server count="
                            + occurrence + " hand=1 nick=client1",
                            Duration.ofSeconds(90)), first.diagnostic());
                }
                assertTrue(host.await(
                        "CP_GDX_E2E_RECONNECTED peer=client1 count=2 hand=1",
                        Duration.ofSeconds(90)), host.diagnostic());
                assertTrue(second.await(
                        "CP_GDX_E2E_ACTION_GATE_REACHED scenario=" + scenario
                        + " nick=client2 hand=2", Duration.ofSeconds(60)),
                        second.diagnostic());
                second.send("DROP_SOCKET");
                assertTrue(second.await(
                        "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client2"
                        + " hand=2 count=1", Duration.ofSeconds(30)),
                        second.diagnostic());
                assertTrue(second.await(
                        "CP_GDX_E2E_RECONNECTED peer=server count=1"
                        + " hand=2 nick=client2", Duration.ofSeconds(90)),
                        second.diagnostic());
                assertTrue(host.await(
                        "CP_GDX_E2E_RECONNECTED peer=client2 count=1 hand=2",
                        Duration.ofSeconds(90)), host.diagnostic());
                assertTrue(!first.contains("RECONNECT_DENIED"),
                        first.diagnostic());
                assertTrue(!second.contains("RECONNECT_DENIED"),
                        second.diagnostic());
            } else if (scenario.equals("reconnect-every-street")) {
                String[] streets = {"PREFLOP", "FLOP", "TURN", "RIVER"};
                NodeProcess reconnecting = nodes.get(1);
                for (int index = 0; index < streets.length; index++) {
                    int hand = index + 1;
                    String suffix = " hand=" + hand + " street="
                            + streets[index];
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_ACTION_GATE_REACHED scenario="
                            + scenario + " nick=client1" + suffix,
                            Duration.ofSeconds(60)), reconnecting.diagnostic());
                    reconnecting.send("DROP_SOCKET");
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client1"
                            + suffix, Duration.ofSeconds(30)),
                            reconnecting.diagnostic());
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_RECONNECTED peer=server count=" + hand
                            + suffix, Duration.ofSeconds(90)),
                            reconnecting.diagnostic());
                    assertTrue(host.await(
                            "CP_GDX_E2E_RECONNECTED peer=client1 count=" + hand
                            + suffix, Duration.ofSeconds(90)),
                            host.diagnostic());
                    assertTrue(!reconnecting.contains("RECONNECT_DENIED"),
                            reconnecting.diagnostic());
                }
            } else if (scenario.equals("reconnect-midhand")
                    || scenario.equals("reconnect-twice")) {
                int reconnects = scenario.equals("reconnect-twice") ? 2 : 1;
                for (int index = 1; index <= reconnects; index++) {
                    NodeProcess reconnecting = nodes.get(index);
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_ACTION_GATE_REACHED scenario="
                            + scenario + " nick=client" + index
                            + " hand=" + index,
                            Duration.ofSeconds(45)), reconnecting.diagnostic());
                    reconnecting.send("DROP_SOCKET");
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_SOCKET_DROP_REQUESTED nick=client"
                            + index + " hand=" + index,
                            Duration.ofSeconds(30)), reconnecting.diagnostic());
                    assertTrue(reconnecting.await(
                            "CP_GDX_E2E_RECONNECTED peer=server count=1 hand="
                            + index,
                            Duration.ofSeconds(60)), reconnecting.diagnostic());
                    assertTrue(host.await(
                            "CP_GDX_E2E_RECONNECTED peer=client" + index
                            + " count=1 hand=" + index,
                            Duration.ofSeconds(60)), host.diagnostic());
                    assertTrue(!reconnecting.contains("RECONNECT_DENIED"),
                            reconnecting.diagnostic());
                }
            } else if ("pause-resume".equals(scenario)) {
                assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED",
                        Duration.ofSeconds(30)), host.diagnostic());
                host.send("PAUSE_TOGGLE");
                for (NodeProcess node : nodes) {
                    assertTrue(node.await("CP_GDX_E2E_PAUSE_STATE paused=true",
                            Duration.ofSeconds(30)), node.diagnostic());
                }
                host.send("PAUSE_TOGGLE");
                for (NodeProcess node : nodes) {
                    assertTrue(node.await("CP_GDX_E2E_PAUSE_STATE paused=false",
                            Duration.ofSeconds(30)), node.diagnostic());
                }
            } else if ("dual-abrupt-exit".equals(scenario)) {
                NodeProcess first = nodes.get(1);
                NodeProcess second = nodes.get(2);
                assertTrue(first.await("CP_GDX_E2E_ACTION_GATE_REACHED",
                        Duration.ofSeconds(30)), first.diagnostic());
                first.destroyForcibly();
                second.destroyForcibly();
                killed.add(first);
                killed.add(second);
            } else if ("mixed-exit-crash".equals(scenario)) {
                NodeProcess departing = nodes.get(1);
                NodeProcess crashed = nodes.get(2);
                assertTrue(departing.await("CP_GDX_E2E_ACTION_GATE_REACHED",
                        Duration.ofSeconds(30)), departing.diagnostic());
                departing.send("CONTROLLED_EXIT");
                assertTrue(departing.await("CP_GDX_E2E_CONTROLLED_EXIT_SENT",
                        Duration.ofSeconds(30)), departing.diagnostic());
                crashed.destroyForcibly();
                killed.add(crashed);
            } else if (disruption != null) {
                NodeProcess disrupted = nodes.get(1);
                assertTrue(disrupted.await("CP_GDX_E2E_ACTION_GATE_REACHED",
                        Duration.ofSeconds(30)), disrupted.diagnostic());
                if ("CRASH_PROCESS".equals(disruption)) {
                    disrupted.destroyForcibly();
                    killed.add(disrupted);
                } else {
                    disrupted.send(disruption);
                    if ("controlled-exit".equals(scenario)) {
                        assertTrue(disrupted.await(
                                "CP_GDX_E2E_CONTROLLED_EXIT_SENT nick=client1",
                                Duration.ofSeconds(30)), disrupted.diagnostic());
                        assertTrue(host.await(
                                "QA EXIT_TESTAMENT_ACCEPTED nick=client1",
                                Duration.ofSeconds(30)), host.diagnostic());
                    }
                }
            }
            List<NodeProcess> completed = new ArrayList<>();
            for (NodeProcess node : nodes) {
                if (killed.contains(node)) {
                    assertTrue(node.awaitExit(Duration.ofSeconds(10)) != 0
                                    || !node.process.isAlive(),
                            node.diagnostic());
                    continue;
                }
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE",
                        completionTimeout(hands, clients + bots + 1)),
                        node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                completed.add(node);
            }
            assertMatchingConservedLedgers(completed, scenario,
                    clients + bots + 1);
            if (TERMINAL_MISDEAL_SCENARIOS.contains(scenario)) {
                assertTerminalMisdealOutcome(completed, host, scenario);
            } else {
                assertMatchingCompletedHistory(completed, scenario, hands);
            }
            if (scenario.equals("controlled-exit")
                    || scenario.equals("allin-controlled-exit")) {
                for (NodeProcess survivor : completed.stream()
                        .filter(node -> !node.name.endsWith(":client1"))
                        .toList()) {
                    assertTrue(!survivor.contains("MISDEAL triggered:"),
                            survivor.diagnostic());
                    assertTrue(!survivor.contains("MANO ANULADA"),
                            survivor.diagnostic());
                    assertTrue(!survivor.contains(
                                    "peer.community_unlock_no_testament"),
                            survivor.diagnostic());
                    assertTrue(!survivor.contains(
                                    "CP_GDX_E2E_RECONNECTED peer=client1"),
                            "a voluntary exit was misclassified as reconnectable\n"
                            + survivor.diagnostic());
                    assertTrue(!survivor.contains("RECONNECTANDO"),
                            "a voluntary exit exposed reconnecting state\n"
                            + survivor.diagnostic());
                    assertTrue(survivor.contains(
                                    "CP_GDX_E2E_DEPARTURE nick=client1 label=SE VA"),
                            "a survivor never projected the voluntary departure\n"
                            + survivor.diagnostic());
                }
            }
            if (scenario.equals("spectator-rebuy-cycle")) {
                assertTrue(completed.stream().anyMatch(node -> node.contains(
                                "CP_GDX_E2E_SPECTATOR_CYCLE"
                                + " nick=client1 saw=true requested=true returned=true")
                                || node.contains(
                                "CP_GDX_E2E_SPECTATOR_CYCLE"
                                + " nick=client2 saw=true requested=true returned=true")),
                        "no busted GDX process completed the spectator/rebuy cycle\n"
                        + host.diagnostic());
                assertTrue(java.util.Arrays.stream(host.capital().split(","))
                                .anyMatch(row -> Integer.parseInt(row.substring(
                                        row.lastIndexOf('/') + 1)) > 0),
                        "spectator-rebuy-cycle persisted no rebuy\n"
                        + host.diagnostic());
            }
            if (scenario.equals("allin-reconnect")) {
                for (NodeProcess node : completed) {
                    assertTrue(!node.contains("invalid atomic POTCARDS"),
                            node.diagnostic());
                    assertTrue(!node.contains("missing mandatory"),
                            node.diagnostic());
                    assertTrue(!node.contains("RECONNECT_DENIED"),
                            node.diagnostic());
                }
            }
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runCrashRejoinRecoverScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        Path clientHome = root.resolve("client-1");
        List<NodeProcess> nodes = new ArrayList<>();
        NodeProcess crashed = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 1, 2, 2, "crash-rejoin-recover",
                    "initial");
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            crashed = startNode(clientHome, "client", "client1", port,
                    1, 2, 2, "crash-rejoin-recover", "initial");
            nodes.add(crashed);
            assertTrue(crashed.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), crashed.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED hands=2",
                    Duration.ofSeconds(20)), host.diagnostic());
            assertTrue(crashed.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=crash-rejoin-recover nick=client1 hand=1",
                    Duration.ofSeconds(60)), crashed.diagnostic());
            crashed.destroyForcibly();
            assertTrue(host.await("CP_GDX_E2E_RECOVERABLE_STOP hand=1",
                    Duration.ofSeconds(180)), host.diagnostic());

            NodeProcess restarted = startNode(clientHome, "client",
                    "client1", port, 1, 2, 2, "crash-rejoin-recover",
                    "restarted");
            nodes.set(1, restarted);
            assertTrue(restarted.await("CP_GDX_E2E_READY"
                    + " role=client nick=client1", Duration.ofSeconds(60)),
                    restarted.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_LOBBY_READY players=4",
                    Duration.ofSeconds(120)), host.diagnostic());
            assertTrue(restarted.await(
                    "CP_GDX_E2E_RECOVERY_LOBBY_READY players=4",
                    Duration.ofSeconds(120)), restarted.diagnostic());
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED hand=2",
                    Duration.ofSeconds(30)), host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE"
                        + " hands=1 durableHands=2 reason=COMPLETED",
                        Duration.ofSeconds(180)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("RECONNECT_DENIED"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes, "crash-rejoin-recover", 4);
            assertEquals(1, host.count("MISDEAL triggered:"),
                    "the single crash must cause exactly one recoverable hand"
                    + " cancellation\n" + host.diagnostic());
        } finally {
            if (crashed != null) {
                crashed.close();
            }
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runLiveHotJoinScenario(Path root, int incumbentClients,
            int bots) throws Exception {
        runLiveHotJoinScenario(root, incumbentClients, bots, "live-hot-join");
    }

    private static void runLiveHotJoinScenario(Path root, int incumbentClients,
            int bots, String scenario) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        int totalClients = incumbentClients + 1;
        int initialPlayers = 1 + incumbentClients + bots;
        String newcomerNickname = "client" + totalClients;
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, totalClients, bots, 3,
                    scenario);
            nodes.add(host);
            awaitHostReady(host);
            for (int index = 1; index <= incumbentClients; index++) {
                NodeProcess incumbent = startNode(root.resolve(
                        "client-" + index), "client", "client" + index,
                        port, totalClients, bots, 3, scenario);
                nodes.add(incumbent);
                assertTrue(incumbent.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), incumbent.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players="
                            + initialPlayers,
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            NodeProcess newcomer = startNode(root.resolve(
                    "client-" + totalClients), "client",
                    newcomerNickname, port, totalClients, bots, 3,
                    scenario, "late");
            nodes.add(newcomer);
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick="
                            + newcomerNickname,
                    Duration.ofSeconds(90)), newcomer.diagnostic());
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_REMOTE_CARD_BACKS nick="
                            + newcomerNickname,
                    Duration.ofSeconds(45)), newcomer.diagnostic());
            if ("live-hot-join-flop-bootstrap".equals(scenario)) {
                assertTrue(newcomer.await(
                        "CP_GDX_E2E_HOT_JOIN_BOOTSTRAP_PUBLIC_STATE "
                        + "faceUp=3 remoteBacks=true pendingBacks=true",
                        Duration.ofSeconds(45)), newcomer.diagnostic());
            }
            host.send("RELEASE_HOT_JOIN");
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_SERVER_NOTIFIED nick="
                            + newcomerNickname,
                    Duration.ofSeconds(45)), host.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_LOCAL_CARDS_INTACT nick=server",
                    Duration.ofSeconds(45)), host.diagnostic());
            for (int index = 1; index <= incumbentClients; index++) {
                NodeProcess incumbent = nodes.get(index);
                assertTrue(incumbent.await(
                        "CP_GDX_E2E_HOT_JOIN_PEER_NOTIFIED nick="
                                + newcomerNickname,
                        Duration.ofSeconds(45)), incumbent.diagnostic());
            }
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_TIMER_SYNC nick="
                            + newcomerNickname,
                    Duration.ofSeconds(120)), newcomer.diagnostic());
            if ("live-hot-join-flop-bootstrap".equals(scenario)) {
                assertTrue(newcomer.await(
                        "CP_GDX_E2E_HOT_JOIN_COMMUNITY_REVEAL nick="
                        + newcomerNickname,
                        Duration.ofSeconds(120)), newcomer.diagnostic());
            }
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_ADMITTED nick="
                            + newcomerNickname,
                    Duration.ofSeconds(150)), newcomer.diagnostic());

            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            for (NodeProcess node : nodes) node.close();
        }
    }

    private static void runTwoConcurrentHotJoinsScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3, "live-hot-join-two");
            nodes.add(host);
            awaitHostReady(host);
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=2",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            NodeProcess newcomer1 = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "live-hot-join-two", "late");
            NodeProcess newcomer2 = startNode(root.resolve("client-2"),
                    "client", "client2", port, 2, 1, 3,
                    "live-hot-join-two", "late");
            nodes.add(newcomer1);
            nodes.add(newcomer2);
            assertTrue(newcomer1.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client1",
                    Duration.ofSeconds(105)), newcomer1.diagnostic());
            assertTrue(newcomer2.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(105)), newcomer2.diagnostic());
            host.send("RELEASE_HOT_JOIN");

            for (NodeProcess newcomer : List.of(newcomer1, newcomer2)) {
                assertTrue(newcomer.await("CP_GDX_E2E_HOT_JOIN_TIMER_SYNC",
                        Duration.ofSeconds(150)), newcomer.diagnostic());
                assertTrue(newcomer.await("CP_GDX_E2E_HOT_JOIN_ADMITTED",
                        Duration.ofSeconds(210)), newcomer.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(25)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            for (NodeProcess node : nodes) node.close();
        }
    }

    private static void runConcurrentHotJoinExitScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> survivors = new ArrayList<>();
        NodeProcess exiting = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3, "live-hot-join-two-exit");
            survivors.add(host);
            awaitHostReady(host);
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=2",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            NodeProcess admitted = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "live-hot-join-two-exit", "late");
            exiting = startNode(root.resolve("client-2"),
                    "client", "client2", port, 2, 1, 3,
                    "live-hot-join-two-exit", "late");
            survivors.add(admitted);
            assertTrue(admitted.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client1",
                    Duration.ofSeconds(105)), admitted.diagnostic());
            assertTrue(exiting.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(105)), exiting.diagnostic());

            host.send("EXIT_HOT_JOIN");
            exiting.send("EXIT_HOT_JOIN");
            assertTrue(exiting.await(
                    "CP_GDX_E2E_HOT_JOIN_EXITED nick=client2",
                    Duration.ofSeconds(60)), exiting.diagnostic());
            assertEquals(0, exiting.awaitExit(Duration.ofSeconds(20)),
                    exiting.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(admitted.await(
                    "CP_GDX_E2E_HOT_JOIN_ADMITTED nick=client1",
                    Duration.ofSeconds(210)), admitted.diagnostic());

            for (NodeProcess node : survivors) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(25)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(survivors);
            audited.add(exiting);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (exiting != null) exiting.close();
            for (NodeProcess node : survivors) node.close();
        }
    }

    private static void runWarmingHotJoinStopScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 1, 1, 3, "live-hot-join-stop");
            nodes.add(host);
            awaitHostReady(host);
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=2",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            NodeProcess newcomer = startNode(root.resolve("client-1"),
                    "client", "client1", port, 1, 1, 3,
                    "live-hot-join-stop", "late");
            nodes.add(newcomer);
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client1",
                    Duration.ofSeconds(90)), newcomer.diagnostic());
            host.send("STOP_HOT_JOIN");

            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_STOPPED",
                        Duration.ofSeconds(120)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            for (NodeProcess node : nodes) node.close();
        }
    }

    private static void runWarmingHotJoinExitScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> tableNodes = new ArrayList<>();
        NodeProcess newcomer = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3, "live-hot-join-exit");
            tableNodes.add(host);
            awaitHostReady(host);
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "live-hot-join-exit");
            tableNodes.add(incumbent);
            assertTrue(incumbent.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), incumbent.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=3",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            newcomer = startNode(root.resolve("client-2"), "client",
                    "client2", port, 2, 1, 3,
                    "live-hot-join-exit", "late");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(90)), newcomer.diagnostic());

            host.send("EXIT_HOT_JOIN");
            newcomer.send("EXIT_HOT_JOIN");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_EXITED nick=client2",
                    Duration.ofSeconds(60)), newcomer.diagnostic());
            assertEquals(0, newcomer.awaitExit(Duration.ofSeconds(20)),
                    newcomer.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), incumbent.diagnostic());

            for (NodeProcess node : tableNodes) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(tableNodes);
            audited.add(newcomer);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (newcomer != null) newcomer.close();
            for (NodeProcess node : tableNodes) node.close();
        }
    }

    private static void runWarmingHotJoinBootstrapExitScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> tableNodes = new ArrayList<>();
        NodeProcess newcomer = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3,
                    "live-hot-join-bootstrap-exit");
            tableNodes.add(host);
            awaitHostReady(host);
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "live-hot-join-bootstrap-exit");
            tableNodes.add(incumbent);
            assertTrue(incumbent.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), incumbent.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=3",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            newcomer = startNode(root.resolve("client-2"), "client",
                    "client2", port, 2, 1, 3,
                    "live-hot-join-bootstrap-exit", "late-bootstrap-exit");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_BOOTSTRAP_ATTACHED nick=client2",
                    Duration.ofSeconds(90)), newcomer.diagnostic());

            // Let the host enter its cancellation branch only after the late
            // table exists, then close the late peer without waiting for its
            // bootstrap/history/timer convergence markers.
            host.send("EXIT_HOT_JOIN");
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_SERVER_NOTIFIED nick=client2",
                    Duration.ofSeconds(60)), host.diagnostic());
            newcomer.send("EXIT_DURING_BOOTSTRAP");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_EXITED nick=client2 phase=bootstrap",
                    Duration.ofSeconds(60)), newcomer.diagnostic());
            assertEquals(0, newcomer.awaitExit(Duration.ofSeconds(20)),
                    newcomer.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), incumbent.diagnostic());

            for (NodeProcess node : tableNodes) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(tableNodes);
            audited.add(newcomer);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (newcomer != null) newcomer.close();
            for (NodeProcess node : tableNodes) node.close();
        }
    }

    private static void runNewlyAdmittedHotJoinExitScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> tableNodes = new ArrayList<>();
        NodeProcess newcomer = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3,
                    "live-hot-join-admission-exit");
            tableNodes.add(host);
            awaitHostReady(host);
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "live-hot-join-admission-exit");
            tableNodes.add(incumbent);
            assertTrue(incumbent.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), incumbent.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=3",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            newcomer = startNode(root.resolve("client-2"), "client",
                    "client2", port, 2, 1, 3,
                    "live-hot-join-admission-exit", "late");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(90)), newcomer.diagnostic());
            host.send("RELEASE_HOT_JOIN");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_ADMISSION_BOUNDARY nick=client2",
                    Duration.ofSeconds(180)), newcomer.diagnostic());
            newcomer.send("EXIT_AFTER_ADMISSION");
            assertTrue(newcomer.await(
                    "CP_GDX_E2E_HOT_JOIN_EXITED nick=client2 phase=admission",
                    Duration.ofSeconds(75)), newcomer.diagnostic());
            assertEquals(0, newcomer.awaitExit(Duration.ofSeconds(20)),
                    newcomer.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2 phase=admission",
                    Duration.ofSeconds(90)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2 phase=admission",
                    Duration.ofSeconds(90)), incumbent.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_FOLD_AUDITED nick=client2",
                    Duration.ofSeconds(90)), host.diagnostic());

            for (NodeProcess node : tableNodes) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(tableNodes);
            audited.add(newcomer);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (newcomer != null) newcomer.close();
            for (NodeProcess node : tableNodes) node.close();
        }
    }

    private static void runWarmingHotJoinReentryScenario(Path root,
            boolean laterHand)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        NodeProcess first = null;
        NodeProcess impostor = null;
        NodeProcess reentered = null;
        try {
            String scenario = laterHand
                    ? "live-hot-join-reentry-later"
                    : "live-hot-join-reentry";
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3,
                    scenario);
            nodes.add(host);
            awaitHostReady(host);
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    scenario);
            nodes.add(incumbent);
            assertTrue(incumbent.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), incumbent.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=3",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            Path ownerHome = root.resolve("client-2-owner");
            first = startNode(ownerHome, "client", "client2", port,
                    2, 1, 3, scenario, "late-exit");
            assertTrue(first.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(90)), first.diagnostic());
            host.send("EXIT_HOT_JOIN");
            first.send("EXIT_HOT_JOIN");
            assertTrue(first.await(
                    "CP_GDX_E2E_HOT_JOIN_EXITED nick=client2",
                    Duration.ofSeconds(60)), first.diagnostic());
            assertEquals(0, first.awaitExit(Duration.ofSeconds(20)),
                    first.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(60)), incumbent.diagnostic());

            impostor = startNode(root.resolve("client-2-impostor"),
                    "client", "client2", port, 2, 1, 3,
                    scenario, "late-impostor");
            assertTrue(impostor.await(
                    "CP_GDX_E2E_HOT_JOIN_IMPERSONATION_REJECTED nick=client2",
                    Duration.ofSeconds(45)), impostor.diagnostic());
            assertEquals(0, impostor.awaitExit(Duration.ofSeconds(20)),
                    impostor.diagnostic());

            assertTrue(host.await(laterHand
                    ? "CP_GDX_E2E_HOT_JOIN_REENTRY_GATE hand=2"
                    : "CP_GDX_E2E_HOT_JOIN_REENTRY_GATE street=TURN",
                    Duration.ofSeconds(120)), host.diagnostic());

            // Reuse the exact profile directory: the persistent Ed25519
            // identity, not the nickname string, owns the old stack.
            reentered = startNode(ownerHome, "client", "client2", port,
                    2, 1, 3, scenario, "late-reentry");
            assertTrue(reentered.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(90)), reentered.diagnostic());
            host.send("REENTER_HOT_JOIN");
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(60)), incumbent.diagnostic());
            assertTrue(reentered.await(
                    "CP_GDX_E2E_HOT_JOIN_REENTRY_ADMITTED nick=client2",
                    Duration.ofSeconds(180)), reentered.diagnostic());
            assertTrue(reentered.await(laterHand
                    ? "CP_GDX_E2E_HOT_JOIN_REENTRY_BOARD_STABLE faceUp=0"
                    : "CP_GDX_E2E_HOT_JOIN_REENTRY_BOARD_STABLE faceUp=4",
                    Duration.ofSeconds(180)), reentered.diagnostic());

            List<NodeProcess> completing = new ArrayList<>(nodes);
            completing.add(reentered);
            for (NodeProcess node : completing) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(completing);
            audited.add(first);
            audited.add(impostor);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (first != null) first.close();
            if (impostor != null) impostor.close();
            if (reentered != null) reentered.close();
            for (NodeProcess node : nodes) node.close();
        }
    }

    private static void runWarmingHotJoinCrashReentryScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> survivors = new ArrayList<>();
        NodeProcess crashed = null;
        NodeProcess reentered = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3,
                    "live-hot-join-crash-reentry");
            survivors.add(host);
            awaitHostReady(host);
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "live-hot-join-crash-reentry");
            survivors.add(incumbent);
            assertTrue(incumbent.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), incumbent.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=3",
                    Duration.ofSeconds(45)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_HOT_JOIN_GATE",
                    Duration.ofSeconds(90)), host.diagnostic());

            Path ownerHome = root.resolve("client-2-owner");
            crashed = startNode(ownerHome, "client", "client2", port,
                    2, 1, 3, "live-hot-join-crash-reentry", "late-exit");
            assertTrue(crashed.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(90)), crashed.diagnostic());

            // No EXIT frame, no graceful shutdown and no opportunity for the
            // client to clean its socket. This is the real power/network-loss
            // edge: the host must retire only that transport generation, cancel
            // the pending seat on every survivor and keep the table alive.
            crashed.killAbruptly();
            assertTrue(crashed.awaitExit(Duration.ofSeconds(20)) != 0,
                    "forcibly terminated warming process exited normally\n"
                    + crashed.diagnostic());
            host.send("EXIT_HOT_JOIN");
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(90)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(90)), incumbent.diagnostic());

            // Reuse the exact profile and Ed25519 identity after an unclean
            // death. A stale loss callback for the old socket must not cancel
            // this new authenticated incarnation.
            reentered = startNode(ownerHome, "client", "client2", port,
                    2, 1, 3, "live-hot-join-crash-reentry", "late-reentry");
            assertTrue(reentered.await(
                    "CP_GDX_E2E_HOT_JOIN_WARMING nick=client2",
                    Duration.ofSeconds(105)), reentered.diagnostic());
            host.send("REENTER_HOT_JOIN");
            assertTrue(host.await(
                    "CP_GDX_E2E_HOT_JOIN_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(75)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_HOT_JOIN_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(75)), incumbent.diagnostic());
            assertTrue(reentered.await(
                    "CP_GDX_E2E_HOT_JOIN_REENTRY_ADMITTED nick=client2",
                    Duration.ofSeconds(210)), reentered.diagnostic());

            List<NodeProcess> completing = new ArrayList<>(survivors);
            completing.add(reentered);
            for (NodeProcess node : completing) {
                assertTrue(node.await("CP_GDX_E2E_HOT_JOIN_COMPLETE",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(25)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (crashed != null) crashed.close();
            if (reentered != null) reentered.close();
            for (NodeProcess node : survivors) node.close();
        }
    }

    private static void runActivePlayerHotReentryScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> survivors = new ArrayList<>();
        NodeProcess departing = null;
        NodeProcess reentered = null;
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 4,
                    "active-player-hot-reentry");
            survivors.add(host);
            awaitHostReady(host);
            Path ownerHome = root.resolve("client-1-owner");
            departing = startNode(ownerHome, "client", "client1", port,
                    2, 1, 4, "active-player-hot-reentry");
            NodeProcess incumbent = startNode(root.resolve("client-2"),
                    "client", "client2", port, 2, 1, 4,
                    "active-player-hot-reentry");
            survivors.add(incumbent);
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=4",
                    Duration.ofSeconds(60)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(departing.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_EXIT_GATE nick=client1",
                    Duration.ofSeconds(120)), departing.diagnostic());

            // Tell the host to snapshot the owner's effective stack before
            // allowing the old process to deliver its authenticated EXIT.
            host.send("EXPECT_ACTIVE_EXIT");
            departing.send("EXIT_ACTIVE_PLAYER");
            assertTrue(departing.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_EXITED nick=client1",
                    Duration.ofSeconds(75)), departing.diagnostic());
            assertEquals(0, departing.awaitExit(Duration.ofSeconds(25)),
                    departing.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_EXIT_OBSERVED nick=client1",
                    Duration.ofSeconds(75)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_EXIT_OBSERVED nick=client1",
                    Duration.ofSeconds(75)), incumbent.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_EXIT_FOLD_AUDITED nick=client1",
                    Duration.ofSeconds(90)), host.diagnostic());

            reentered = startNode(ownerHome, "client", "client1", port,
                    2, 1, 4, "active-player-hot-reentry", "late-reentry");
            assertTrue(reentered.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_WARMING nick=client1",
                    Duration.ofSeconds(105)), reentered.diagnostic());
            host.send("EXPECT_ACTIVE_REENTRY");
            assertTrue(host.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_WARMING nick=client1",
                    Duration.ofSeconds(75)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_WARMING nick=client1",
                    Duration.ofSeconds(75)), incumbent.diagnostic());
            assertTrue(reentered.await(
                    "CP_GDX_E2E_ACTIVE_REENTRY_ADMITTED nick=client1",
                    Duration.ofSeconds(210)), reentered.diagnostic());

            List<NodeProcess> completing = new ArrayList<>(survivors);
            completing.add(reentered);
            for (NodeProcess node : completing) {
                assertTrue(node.await("CP_GDX_E2E_ACTIVE_REENTRY_COMPLETE",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(25)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(completing);
            audited.add(departing);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (departing != null) departing.close();
            if (reentered != null) reentered.close();
            for (NodeProcess node : survivors) node.close();
        }
    }

    private static void runAdmittedHotJoinActiveReentryScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> survivors = new ArrayList<>();
        NodeProcess departing = null;
        NodeProcess reentered = null;
        try {
            String scenario = "admitted-hot-join-active-reentry";
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 4, scenario);
            survivors.add(host);
            awaitHostReady(host);
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 4, scenario);
            survivors.add(incumbent);
            assertTrue(incumbent.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), incumbent.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_LOBBY_READY players=3",
                    Duration.ofSeconds(60)), host.diagnostic());
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_ADMITTED_REENTRY_INITIAL_GATE",
                    Duration.ofSeconds(120)), host.diagnostic());

            Path ownerHome = root.resolve("client-2-owner");
            host.send("EXPECT_LATE_OWNER");
            departing = startNode(ownerHome, "client", "client2", port,
                    2, 1, 4, scenario, "late-first");
            assertTrue(departing.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_FIRST_WARMING nick=client2",
                    Duration.ofSeconds(120)), departing.diagnostic());
            assertTrue(departing.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_EXIT_GATE nick=client2",
                    Duration.ofSeconds(210)), departing.diagnostic());

            host.send("EXPECT_ADMITTED_EXIT");
            departing.send("EXIT_ADMITTED_OWNER");
            assertTrue(departing.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_EXITED nick=client2",
                    Duration.ofSeconds(90)), departing.diagnostic());
            assertEquals(0, departing.awaitExit(Duration.ofSeconds(25)),
                    departing.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_EXIT_FOLD_AUDITED nick=client2",
                    Duration.ofSeconds(120)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_EXIT_OBSERVED nick=client2",
                    Duration.ofSeconds(120)), incumbent.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_RETURN_GATE",
                    Duration.ofSeconds(150)), host.diagnostic());

            host.send("EXPECT_ADMITTED_REENTRY");
            reentered = startNode(ownerHome, "client", "client2", port,
                    2, 1, 4, scenario, "late-reentry");
            assertTrue(reentered.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(135)), reentered.diagnostic());
            assertTrue(host.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(90)), host.diagnostic());
            assertTrue(incumbent.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_WARMING nick=client2",
                    Duration.ofSeconds(90)), incumbent.diagnostic());
            assertTrue(reentered.await(
                    "CP_GDX_E2E_ADMITTED_REENTRY_ADMITTED nick=client2",
                    Duration.ofSeconds(240)), reentered.diagnostic());

            List<NodeProcess> completing = new ArrayList<>(survivors);
            completing.add(reentered);
            for (NodeProcess node : completing) {
                assertTrue(node.await("CP_GDX_E2E_ADMITTED_REENTRY_COMPLETE",
                        Duration.ofSeconds(360)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(25)),
                        node.diagnostic());
            }
            List<NodeProcess> audited = new ArrayList<>(completing);
            audited.add(departing);
            for (NodeProcess node : audited) {
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                for (String fatal : ALWAYS_FATAL_OUTPUT) {
                    assertTrue(!node.contains(fatal),
                            fatal + "\n" + node.diagnostic());
                }
            }
        } finally {
            if (departing != null) departing.close();
            if (reentered != null) reentered.close();
            for (NodeProcess node : survivors) node.close();
        }
    }

    private static void awaitHostReady(NodeProcess host)
            throws Exception {
        assertTrue(host.await("CP_GDX_E2E_READY role=host",
                Duration.ofSeconds(30)), host.diagnostic());
    }

    private static void runForceRecoverScenario(Path root) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 1, 2, 2, "force-recover");
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            NodeProcess client = startNode(root.resolve("client-1"),
                    "client", "client1", port, 1, 2, 2,
                    "force-recover");
            nodes.add(client);
            assertTrue(client.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), client.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED hands=2",
                    Duration.ofSeconds(20)), host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=force-recover nick=server hand=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED hand=1",
                    Duration.ofSeconds(30)), host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP hand=1",
                        Duration.ofSeconds(60)), node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port=" + port,
                    Duration.ofSeconds(60)), host.diagnostic());
            client.send("REOPEN_RECOVERY_LOBBY");
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=4",
                        Duration.ofSeconds(120)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=2 cycle=1",
                    Duration.ofSeconds(30)), host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE"
                        + " hands=2 durableHands=2 reason=COMPLETED",
                        Duration.ofSeconds(180)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes, "force-recover", 4);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runDoubleForceRecoverScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 1, 2, 4, "double-force-recover");
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            NodeProcess client = startNode(root.resolve("client-1"),
                    "client", "client1", port, 1, 2, 4,
                    "double-force-recover");
            nodes.add(client);
            assertTrue(client.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), client.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=4 cycle=0", Duration.ofSeconds(20)),
                    host.diagnostic());

            long[] interruptedHands = {1L, 3L};
            for (int index = 0; index < interruptedHands.length; index++) {
                int cycle = index + 1;
                long hand = interruptedHands[index];
                assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                        + " scenario=double-force-recover nick=server hand="
                        + hand + " cycle=" + cycle,
                        Duration.ofSeconds(120)), host.diagnostic());
                host.send("FORCE_RECOVER");
                assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                        + " hand=" + hand + " cycle=" + cycle,
                        Duration.ofSeconds(30)), host.diagnostic());
                for (NodeProcess node : nodes) {
                    assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP hand="
                            + hand + " cycle=" + cycle,
                            Duration.ofSeconds(90)), node.diagnostic());
                }
                assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                        + port + " cycle=" + cycle,
                        Duration.ofSeconds(60)), host.diagnostic());
                client.send("REOPEN_RECOVERY_LOBBY");
                for (NodeProcess node : nodes) {
                    assertTrue(node.await(
                            "CP_GDX_E2E_RECOVERY_LOBBY_READY players=4"
                            + " cycle=" + cycle,
                            Duration.ofSeconds(120)), node.diagnostic());
                }
                host.send("START_RECOVERED_GAME");
                assertTrue(host.await(
                        "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                        + " hands=4 cycle=" + cycle,
                        Duration.ofSeconds(30)), host.diagnostic());
            }

            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE"
                        + " hands=2 durableHands=4 reason=COMPLETED",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes,
                    "double-force-recover", 4);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runReconnectForceRecoverScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 3,
                    "reconnect-force-recover");
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            NodeProcess first = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 3,
                    "reconnect-force-recover");
            NodeProcess second = startNode(root.resolve("client-2"),
                    "client", "client2", port, 2, 1, 3,
                    "reconnect-force-recover");
            nodes.add(first);
            nodes.add(second);
            assertTrue(first.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), first.diagnostic());
            assertTrue(second.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), second.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=3 cycle=0", Duration.ofSeconds(20)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=reconnect-force-recover nick=server hand=1",
                    Duration.ofSeconds(60)), host.diagnostic());

            first.send("DROP_SOCKET");
            assertTrue(first.await("CP_GDX_E2E_SOCKET_DROP_REQUESTED"
                    + " nick=client1 hand=1 before=force-recover",
                    Duration.ofSeconds(30)), first.diagnostic());
            assertTrue(first.await("CP_GDX_E2E_RECONNECT_STARTED"
                    + " peer=server hand=1 before=force-recover",
                    Duration.ofSeconds(60)), first.diagnostic());

            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=1 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=1 cycle=1", Duration.ofSeconds(90)),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());
            first.send("REOPEN_RECOVERY_LOBBY");
            second.send("REOPEN_RECOVERY_LOBBY");
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=4 cycle=1",
                        Duration.ofSeconds(120)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=3 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE"
                        + " hands=3 durableHands=3 reason=COMPLETED",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes,
                    "reconnect-force-recover", 4);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runForceRecoverAddClientScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 2, 2,
                    "force-recover-add-client");
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 2, 2,
                    "force-recover-add-client");
            nodes.add(host);
            nodes.add(incumbent);
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), node.diagnostic());
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=2 cycle=0", Duration.ofSeconds(20)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=force-recover-add-client nick=server hand=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=1 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=1 cycle=1", Duration.ofSeconds(90)),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());

            incumbent.send("REOPEN_RECOVERY_LOBBY");
            NodeProcess newcomer = startNode(root.resolve("client-2"),
                    "client", "client2", port, 2, 2, 2,
                    "force-recover-add-client");
            nodes.add(newcomer);
            newcomer.send("REOPEN_RECOVERY_LOBBY");
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=5 cycle=1",
                        Duration.ofSeconds(120)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=2 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                String renderedHands = node == newcomer ? "1" : "2";
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands="
                        + renderedHands
                        + " durableHands=2 reason=COMPLETED",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes,
                    "force-recover-add-client", 5);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runForceRecoverAddTwoScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 3, 1, 2, "force-recover-add-two");
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            NodeProcess incumbent = startNode(root.resolve("client-1"),
                    "client", "client1", port, 3, 1, 2,
                    "force-recover-add-two");
            nodes.add(host);
            nodes.add(incumbent);
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), node.diagnostic());
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=3",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=2 cycle=0", Duration.ofSeconds(20)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=force-recover-add-two nick=server hand=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=1 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=1 cycle=1", Duration.ofSeconds(90)),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());

            incumbent.send("REOPEN_RECOVERY_LOBBY");
            List<NodeProcess> newcomers = new ArrayList<>();
            for (int index = 2; index <= 3; index++) {
                NodeProcess newcomer = startNode(
                        root.resolve("client-" + index), "client",
                        "client" + index, port, 3, 1, 2,
                        "force-recover-add-two");
                newcomer.send("REOPEN_RECOVERY_LOBBY");
                newcomers.add(newcomer);
                nodes.add(newcomer);
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=5 cycle=1",
                        Duration.ofSeconds(120)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=2 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                String renderedHands = newcomers.contains(node) ? "1" : "2";
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands="
                        + renderedHands
                        + " durableHands=2 reason=COMPLETED",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes,
                    "force-recover-add-two", 5);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runForceRecoverSwapClientScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 2,
                    "force-recover-swap-client");
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            NodeProcess missing = startNode(root.resolve("client-1"),
                    "client", "client1", port, 2, 1, 2,
                    "force-recover-swap-client");
            NodeProcess survivor = startNode(root.resolve("client-2"),
                    "client", "client2", port, 2, 1, 2,
                    "force-recover-swap-client");
            nodes.add(host);
            nodes.add(missing);
            nodes.add(survivor);
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), node.diagnostic());
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=2 cycle=0", Duration.ofSeconds(20)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=force-recover-swap-client nick=server hand=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=1 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=1 cycle=1", Duration.ofSeconds(90)),
                        node.diagnostic());
            }
            assertEquals(0, missing.awaitExit(Duration.ofSeconds(15)),
                    missing.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());

            survivor.send("REOPEN_RECOVERY_LOBBY");
            NodeProcess replacement = startNode(root.resolve("client-3"),
                    "client", "client3", port, 2, 1, 2,
                    "force-recover-swap-client");
            nodes.add(replacement);
            replacement.send("REOPEN_RECOVERY_LOBBY");
            for (NodeProcess node : List.of(host, survivor, replacement)) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=4 cycle=1",
                        Duration.ofSeconds(120)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=2 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            List<NodeProcess> completed = List.of(host, survivor, replacement);
            for (NodeProcess node : completed) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE"
                        + " hands=1 durableHands=2 reason=COMPLETED",
                        Duration.ofSeconds(240)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(15)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(completed,
                    "force-recover-swap-client", 5);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runSpectatorRecoveryMixScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 6, 1, 7,
                    "spectator-recovery-mix");
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            List<NodeProcess> incumbents = new ArrayList<>();
            for (int index = 1; index <= 4; index++) {
                NodeProcess incumbent = startNode(
                        root.resolve("client-" + index), "client",
                        "client" + index, port, 6, 1, 7,
                        "spectator-recovery-mix");
                incumbents.add(incumbent);
                nodes.add(incumbent);
                assertTrue(incumbent.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), incumbent.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=6",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=7 cycle=0", Duration.ofSeconds(30)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=spectator-recovery-mix nick=server hand=4",
                    Duration.ofSeconds(150)), host.diagnostic());
            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=4 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=4 cycle=1", Duration.ofSeconds(120)),
                        node.diagnostic());
            }
            List<NodeProcess> spectators = new ArrayList<>();
            for (int index = 0; index < incumbents.size(); index++) {
                NodeProcess incumbent = incumbents.get(index);
                if (incumbent.contains("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=4 cycle=1 spectator=true nick=client"
                        + (index + 1))) {
                    spectators.add(incumbent);
                }
            }
            assertTrue(spectators.size() >= 2,
                    "at least two independent GDX humans must spectate\n"
                    + host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());

            for (NodeProcess incumbent : incumbents) {
                incumbent.send("REOPEN_RECOVERY_LOBBY");
            }
            List<NodeProcess> newcomers = new ArrayList<>();
            for (int index = 5; index <= 6; index++) {
                NodeProcess newcomer = startNode(
                        root.resolve("client-" + index), "client",
                        "client" + index, port, 6, 1, 7,
                        "spectator-recovery-mix");
                newcomer.send("REOPEN_RECOVERY_LOBBY");
                newcomers.add(newcomer);
                nodes.add(newcomer);
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=8 cycle=1",
                        Duration.ofSeconds(150)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=7 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_REBUY_GATE_REACHED"
                    + " scenario=spectator-recovery-mix nick=server hand=4",
                    Duration.ofSeconds(150)), host.diagnostic());
            for (NodeProcess spectator : spectators) {
                assertTrue(spectator.await("CP_GDX_E2E_REBUY_CHOICE_ACCEPTED",
                        Duration.ofSeconds(60)), spectator.diagnostic());
            }
            host.send("RELEASE_RECOVERY_ACTION");
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_REBUY_GATE_RELEASED"
                    + " hand=4", Duration.ofSeconds(30)), host.diagnostic());

            for (NodeProcess node : nodes) {
                String renderedHands = newcomers.contains(node) ? "3" : "4";
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands="
                        + renderedHands
                        + " durableHands=7 reason=COMPLETED",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertTrue(node.await("CP_GDX_E2E_VALIDATION_READY"
                        + " scenario=spectator-recovery-mix",
                        Duration.ofSeconds(30)), node.diagnostic());
            }
            // Release teardown only after every independent GDX process has
            // checked the complete visible lifecycle.  This preserves the
            // strict no-unexpected-RECONECTANDO oracle while preventing one
            // successful process from disconnecting underneath another.
            for (NodeProcess node : nodes) {
                node.send("FINISH_SCENARIO");
            }
            for (NodeProcess node : nodes) {
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            for (NodeProcess spectator : spectators) {
                assertTrue(spectator.contains("initialSpectator=true"
                        + " requested=true returned=true"),
                        spectator.diagnostic());
            }
            assertMatchingConservedLedgers(nodes,
                    "spectator-recovery-mix", 8);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runBotBustRecoveryScenario(Path root,
            boolean enableBotRebuy) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String scenario = enableBotRebuy ? "bot-bust-recover-regrow"
                : "bot-bust-recover-drop";
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 2, 7, scenario);
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            for (int index = 1; index <= 2; index++) {
                NodeProcess client = startNode(
                        root.resolve("client-" + index), "client",
                        "client" + index, port, 2, 2, 7, scenario);
                nodes.add(client);
                assertTrue(client.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), client.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=5",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=7 cycle=0", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_BUSTED_BOTS nicks=",
                        Duration.ofSeconds(150)), node.diagnostic());
            }
            String busted = nodes.get(0).valueAfter(
                    "CP_GDX_E2E_BUSTED_BOTS nicks=");
            assertTrue(!busted.isBlank(), host.diagnostic());
            for (NodeProcess node : nodes.subList(1, nodes.size())) {
                assertEquals(busted, node.valueAfter(
                        "CP_GDX_E2E_BUSTED_BOTS nicks="),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED scenario="
                    + scenario + " nick=server hand=4",
                    Duration.ofSeconds(150)), host.diagnostic());
            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=4 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=4 cycle=1", Duration.ofSeconds(120)),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1 botRebuy=" + enableBotRebuy,
                    Duration.ofSeconds(60)), host.diagnostic());
            for (NodeProcess node : nodes.subList(1, nodes.size())) {
                node.send("REOPEN_RECOVERY_LOBBY");
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=5 cycle=1",
                        Duration.ofSeconds(150)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=7 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            String outcome = "CP_GDX_E2E_BOT_RECOVERY";
            String expectation = "mode="
                    + (enableBotRebuy ? "regrow" : "drop")
                    + " reactivated=" + enableBotRebuy + " playing="
                    + enableBotRebuy;
            for (NodeProcess node : nodes) {
                assertTrue(node.await(outcome, Duration.ofSeconds(300)),
                        node.diagnostic());
                assertTrue(node.contains(expectation), node.diagnostic());
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands=4"
                        + " durableHands=7 reason=COMPLETED",
                        Duration.ofSeconds(60)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes, scenario, 5);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runHumanBustExitRejoinRebuyScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String scenario = "human-bust-exit-rejoin-rebuy";
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 3, 1, 7, scenario);
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            List<NodeProcess> clients = new ArrayList<>();
            for (int index = 1; index <= 3; index++) {
                NodeProcess client = startNode(
                        root.resolve("client-" + index), "client",
                        "client" + index, port, 3, 1, 7, scenario);
                clients.add(client);
                nodes.add(client);
                assertTrue(client.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), client.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=5",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            assertTrue(host.await("CP_GDX_E2E_GAME_START_REQUESTED"
                    + " hands=7 cycle=0", Duration.ofSeconds(30)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED scenario="
                    + scenario + " nick=server hand=4",
                    Duration.ofSeconds(150)), host.diagnostic());

            NodeProcess first = clients.get(0);
            NodeProcess second = clients.get(1);
            assertTrue(first.await("CP_GDX_E2E_LOCAL_SPECTATOR nick=client1",
                    Duration.ofSeconds(60)), first.diagnostic());
            assertTrue(second.await("CP_GDX_E2E_LOCAL_SPECTATOR nick=client2",
                    Duration.ofSeconds(60)), second.diagnostic());
            NodeProcess departing;
            NodeProcess staying;
            if (first.contains("CP_GDX_E2E_LOCAL_SPECTATOR nick=client1"
                    + " value=true")) {
                departing = first;
                staying = second;
            } else {
                assertTrue(second.contains(
                        "CP_GDX_E2E_LOCAL_SPECTATOR nick=client2 value=true"),
                        "at least one forced all-in human must bust\n"
                        + second.diagnostic());
                departing = second;
                staying = first;
            }
            departing.send("EXIT_AS_SPECTATOR");
            staying.send("STAY_FOR_RECOVERY");
            assertTrue(departing.await("CP_GDX_E2E_CONTROLLED_SPECTATOR_EXIT",
                    Duration.ofSeconds(60)), departing.diagnostic());
            String originalIdentity = departing.valueAfter(" identity=");
            assertTrue(!originalIdentity.isBlank(), departing.diagnostic());

            host.send("FORCE_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_FORCE_RECOVER_REQUESTED"
                    + " hand=4 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            for (NodeProcess node : nodes) {
                if (node != departing) {
                    assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                            + " hand=4 cycle=1", Duration.ofSeconds(120)),
                            node.diagnostic());
                }
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());
            for (NodeProcess client : clients) {
                client.send("REOPEN_RECOVERY_LOBBY");
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=5 cycle=1",
                        Duration.ofSeconds(150)), node.diagnostic());
            }
            assertTrue(departing.contains("identity=" + originalIdentity),
                    departing.diagnostic());
            host.send("START_RECOVERED_GAME");
            assertTrue(host.await(
                    "CP_GDX_E2E_RECOVERED_GAME_START_REQUESTED"
                    + " hands=7 cycle=1", Duration.ofSeconds(30)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_REBUY_GATE_REACHED"
                    + " scenario=" + scenario + " nick=server hand=4",
                    Duration.ofSeconds(150)), host.diagnostic());
            assertTrue(departing.await("CP_GDX_E2E_REBUY_CHOICE_ACCEPTED",
                    Duration.ofSeconds(60)), departing.diagnostic());
            host.send("RELEASE_RECOVERY_ACTION");
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_REBUY_GATE_RELEASED"
                    + " hand=4", Duration.ofSeconds(30)), host.diagnostic());

            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands=4"
                        + " durableHands=7 reason=COMPLETED",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
                assertTrue(!node.contains("Recover action MISMATCH"),
                        node.diagnostic());
            }
            assertTrue(departing.contains("selected=true requested=true"
                    + " returned=true reactivated=true playing=true"),
                    departing.diagnostic());
            assertMatchingConservedLedgers(nodes, scenario, 5);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runSpectatorDoubleRecoveryCrashMixScenario(Path root)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String scenario = "spectator-double-recovery-crash-mix";
        List<NodeProcess> nodes = new ArrayList<>();
        Set<NodeProcess> killed = new HashSet<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 6, 1, 8, scenario);
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            List<NodeProcess> clients = new ArrayList<>();
            for (int index = 1; index <= 4; index++) {
                NodeProcess client = startNode(root.resolve("client-" + index),
                        "client", "client" + index, port, 6, 1, 8,
                        scenario);
                clients.add(client);
                nodes.add(client);
                assertTrue(client.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), client.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=6",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            NodeProcess crashTarget = clients.get(2);
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_SPECTATOR_SETUP",
                        Duration.ofSeconds(180)), node.diagnostic());
            }
            assertTrue(crashTarget.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=" + scenario
                    + " nick=client3 hand=4 cycle=0",
                    Duration.ofSeconds(180)), crashTarget.diagnostic());
            host.send("FORCE_RECOVER");
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=4 cycle=1", Duration.ofSeconds(150)),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());
            for (NodeProcess client : clients) {
                client.send("REOPEN_RECOVERY_LOBBY");
            }
            for (int index = 5; index <= 6; index++) {
                NodeProcess newcomer = startNode(
                        root.resolve("client-" + index), "client",
                        "client" + index, port, 6, 1, 8, scenario);
                newcomer.send("REOPEN_RECOVERY_LOBBY");
                clients.add(newcomer);
                nodes.add(newcomer);
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=8 cycle=1",
                        Duration.ofSeconds(180)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            assertTrue(crashTarget.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=" + scenario
                    + " nick=client3 hand=4 cycle=1",
                    Duration.ofSeconds(180)), crashTarget.diagnostic());
            String crashIdentity = crashTarget.valueAfter(" identity=");
            crashTarget.send("CRASH_NOW");
            assertEquals(23, crashTarget.awaitExit(Duration.ofSeconds(30)),
                    crashTarget.diagnostic());
            killed.add(crashTarget);
            for (NodeProcess node : nodes) {
                if (node != crashTarget) {
                    assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                            + " hand=4 cycle=2", Duration.ofSeconds(180)),
                            node.diagnostic());
                }
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=2", Duration.ofSeconds(60)),
                    host.diagnostic());
            List<NodeProcess> survivors = nodes.stream()
                    .filter(node -> node != crashTarget).toList();
            for (NodeProcess node : survivors) {
                if (node != host) {
                    node.send("REOPEN_RECOVERY_LOBBY");
                }
            }
            NodeProcess restarted = startNode(root.resolve("client-3"),
                    "client", "client3", port, 6, 1, 8, scenario,
                    "second-recovery");
            restarted.send("REOPEN_RECOVERY_LOBBY");
            nodes.add(restarted);
            for (NodeProcess node : survivors) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=8 cycle=2",
                        Duration.ofSeconds(180)), node.diagnostic());
            }
            assertTrue(restarted.await(
                    "CP_GDX_E2E_RECOVERY_LOBBY_READY players=8 cycle=2",
                    Duration.ofSeconds(180)), restarted.diagnostic());
            assertTrue(crashIdentity.isBlank()
                    || restarted.contains("identity=" + crashIdentity),
                    restarted.diagnostic());
            host.send("START_RECOVERED_GAME");
            assertTrue(restarted.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=" + scenario
                    + " nick=client3 hand=5 cycle=2",
                    Duration.ofSeconds(180)), restarted.diagnostic());
            restarted.send("RELEASE_FINAL_ACTION");
            assertTrue(restarted.await("CP_GDX_E2E_FINAL_ACTION_RELEASED"
                    + " nick=client3 hand=5", Duration.ofSeconds(30)),
                    restarted.diagnostic());

            List<NodeProcess> completed = new ArrayList<>(survivors);
            completed.add(restarted);
            for (NodeProcess node : completed) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands=4"
                        + " durableHands=8 reason=COMPLETED",
                        Duration.ofSeconds(360)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(completed, scenario, 8);
            assertEquals(1, host.count("MISDEAL triggered:"),
                    "only the injected crash may cancel a hand\n"
                    + host.diagnostic());
        } finally {
            for (NodeProcess node : nodes) {
                if (!killed.contains(node)) {
                    node.close();
                }
            }
        }
    }

    private static void runTransportChaosScenario(Path root) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String scenario = "transport-chaos";
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 3, 1, 5, scenario);
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            List<NodeProcess> clients = new ArrayList<>();
            for (int index = 1; index <= 3; index++) {
                NodeProcess client = startNode(root.resolve("client-" + index),
                        "client", "client" + index, port, 3, 1, 5,
                        scenario);
                clients.add(client);
                nodes.add(client);
                assertTrue(client.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), client.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=5",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            NodeProcess first = clients.get(0);
            NodeProcess second = clients.get(1);
            assertTrue(first.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=transport-chaos nick=client1 hand=1",
                    Duration.ofSeconds(90)), first.diagnostic());
            first.send("DROP_SOCKET");
            second.send("DROP_SOCKET");
            assertTrue(first.await("CP_GDX_E2E_RECONNECTED peer=server"
                    + " count=1 nick=client1", Duration.ofSeconds(90)),
                    first.diagnostic());
            assertTrue(second.await("CP_GDX_E2E_RECONNECTED peer=server"
                    + " count=1 nick=client2", Duration.ofSeconds(90)),
                    second.diagnostic());
            first.send("DROP_SOCKET_AGAIN");
            assertTrue(first.await("CP_GDX_E2E_RECONNECTED peer=server"
                    + " count=2 nick=client1", Duration.ofSeconds(90)),
                    first.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_TRANSPORT_CUTS_COMPLETE"
                    + " client1=2 client2=1", Duration.ofSeconds(120)),
                    host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=transport-chaos nick=server hand=2",
                    Duration.ofSeconds(120)), host.diagnostic());
            host.send("PAUSE_RESUME_RECOVER");
            assertTrue(host.await("CP_GDX_E2E_PAUSE_STATE paused=true",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_PAUSE_STATE paused=false",
                    Duration.ofSeconds(60)), host.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP"
                        + " hand=2 cycle=1", Duration.ofSeconds(150)),
                        node.diagnostic());
            }
            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());
            for (NodeProcess client : clients) {
                client.send("REOPEN_RECOVERY_LOBBY");
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await(
                        "CP_GDX_E2E_RECOVERY_LOBBY_READY players=5 cycle=1",
                        Duration.ofSeconds(150)), node.diagnostic());
            }
            host.send("START_RECOVERED_GAME");
            NodeProcess third = clients.get(2);
            assertTrue(third.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=transport-chaos nick=client3 hand=4",
                    Duration.ofSeconds(240)), third.diagnostic());
            third.send("DROP_SOCKET");
            assertTrue(third.await("CP_GDX_E2E_RECONNECTED peer=server"
                    + " count=1 nick=client3", Duration.ofSeconds(90)),
                    third.diagnostic());
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands=4"
                        + " durableHands=5 reason=COMPLETED",
                        Duration.ofSeconds(300)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes, scenario, 5);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void runLifecycleChaosScenario(Path root) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String scenario = "lifecycle-chaos";
        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host",
                    "server", port, 2, 1, 7, scenario);
            nodes.add(host);
            assertTrue(host.await("CP_GDX_E2E_READY",
                    Duration.ofSeconds(30)), host.diagnostic());
            List<NodeProcess> clients = new ArrayList<>();
            for (int index = 1; index <= 2; index++) {
                NodeProcess client = startNode(root.resolve("client-" + index),
                        "client", "client" + index, port, 2, 1, 7,
                        scenario);
                clients.add(client);
                nodes.add(client);
                assertTrue(client.await("CP_GDX_E2E_READY",
                        Duration.ofSeconds(30)), client.diagnostic());
            }
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_LOBBY_READY players=4",
                        Duration.ofSeconds(45)), node.diagnostic());
            }
            host.send("START_GAME");
            NodeProcess first = clients.get(0);
            assertTrue(first.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=lifecycle-chaos nick=client1 hand=1",
                    Duration.ofSeconds(90)), first.diagnostic());
            first.send("DROP_SOCKET");
            assertTrue(first.await("CP_GDX_E2E_RECONNECTED peer=server"
                    + " count=1 nick=client1", Duration.ofSeconds(90)),
                    first.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=lifecycle-chaos nick=server hand=2",
                    Duration.ofSeconds(150)), host.diagnostic());
            host.send("PAUSE_RESUME_CONTINUE");
            assertTrue(host.await("CP_GDX_E2E_PAUSE_STATE paused=true cycle=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_PAUSE_STATE paused=false cycle=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_HAND_THREE_STOP_ARMED cycle=1",
                    Duration.ofSeconds(60)), host.diagnostic());
            assertTrue(awaitLifecycleActionGate(nodes, 3,
                    Duration.ofSeconds(150)), diagnostics(nodes));
            host.send("STOP_RECOVERY");
            awaitRecoverableCycle(nodes, 3, 1);

            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=1", Duration.ofSeconds(60)),
                    host.diagnostic());
            for (NodeProcess client : clients) {
                client.send("REOPEN_RECOVERY_LOBBY");
            }
            awaitRecoveryLobby(nodes, 4, 1);
            host.send("START_RECOVERED_GAME");
            NodeProcess second = clients.get(1);
            assertTrue(second.await("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=lifecycle-chaos nick=client2 hand=5",
                    Duration.ofSeconds(240)), second.diagnostic());
            second.send("DROP_SOCKET");
            assertTrue(second.await("CP_GDX_E2E_RECONNECTED peer=server"
                    + " count=1 nick=client2", Duration.ofSeconds(90)),
                    second.diagnostic());
            assertTrue(host.await("CP_GDX_E2E_SECOND_RECONNECT_SEEN"
                    + " nick=client2 count=1", Duration.ofSeconds(90)),
                    host.diagnostic());
            assertTrue(awaitLifecycleActionGate(nodes, 6,
                    Duration.ofSeconds(240)), diagnostics(nodes));
            host.send("STOP_RECOVERY");
            awaitRecoverableCycle(nodes, 6, 2);

            assertTrue(host.await("CP_GDX_E2E_RECOVERY_HOST_OPEN port="
                    + port + " cycle=2", Duration.ofSeconds(60)),
                    host.diagnostic());
            for (NodeProcess client : clients) {
                client.send("REOPEN_RECOVERY_LOBBY");
            }
            awaitRecoveryLobby(nodes, 4, 2);
            host.send("START_RECOVERED_GAME");
            for (NodeProcess node : nodes) {
                assertTrue(node.await("CP_GDX_E2E_HANDS_COMPLETE hands=2"
                        + " durableHands=7 reason=COMPLETED",
                        Duration.ofSeconds(360)), node.diagnostic());
                assertEquals(0, node.awaitExit(Duration.ofSeconds(20)),
                        node.diagnostic());
                assertTrue(!node.contains("CP_GDX_E2E_FAIL"),
                        node.diagnostic());
                assertTrue(!node.contains("TABLE_FAILURE_V1"),
                        node.diagnostic());
            }
            assertMatchingConservedLedgers(nodes, scenario, 4);
        } finally {
            for (NodeProcess node : nodes) {
                node.close();
            }
        }
    }

    private static void awaitRecoverableCycle(List<NodeProcess> nodes,
            int hand, int cycle) throws Exception {
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_GDX_E2E_RECOVERABLE_STOP hand=" + hand
                    + " cycle=" + cycle, Duration.ofSeconds(180)),
                    node.diagnostic());
        }
    }

    private static void awaitRecoveryLobby(List<NodeProcess> nodes,
            int players, int cycle) throws Exception {
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_GDX_E2E_RECOVERY_LOBBY_READY players="
                    + players + " cycle=" + cycle,
                    Duration.ofSeconds(180)), node.diagnostic());
        }
    }

    private static boolean awaitLifecycleActionGate(List<NodeProcess> nodes,
            int hand, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (nodes.stream().anyMatch(node -> node.contains(
                    "CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=lifecycle-chaos nick=server hand=" + hand)
                    || node.contains("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=lifecycle-chaos nick=client1 hand=" + hand)
                    || node.contains("CP_GDX_E2E_ACTION_GATE_REACHED"
                    + " scenario=lifecycle-chaos nick=client2 hand=" + hand))) {
                return true;
            }
            Thread.sleep(10L);
        }
        return false;
    }

    private static String diagnostics(List<NodeProcess> nodes) {
        return nodes.stream().map(NodeProcess::diagnostic)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static void assertMatchingConservedLedgers(
            List<NodeProcess> completed, String scenario, int seats) {
        for (NodeProcess node : completed) {
            assertEquals(1, node.count("CP_GDX_E2E_LEDGER "),
                    "node must publish exactly one terminal ledger\n"
                    + node.diagnostic());
            assertEquals(1, node.count("CP_GDX_E2E_CAPITAL "),
                    "node must publish exactly one terminal capital summary\n"
                    + node.diagnostic());
            assertEquals(1, node.count("CP_GDX_E2E_HANDS_COMPLETE "),
                    "node must publish exactly one terminal hand summary\n"
                    + node.diagnostic());
        }
        List<NodeProcess> comparable = completed.stream()
                .filter(node -> !(scenario.equals("controlled-exit")
                        || scenario.equals("mixed-exit-crash")
                        || scenario.equals("allin-controlled-exit"))
                        || !node.name.endsWith(":client1"))
                .toList();
        int minimumWitnesses = 2;
        assertTrue(comparable.size() >= minimumWitnesses,
                "scenario requires at least " + minimumWitnesses
                + " surviving ledger witness(es)");
        String expected = comparable.get(0).ledger();
        String expectedCapital = comparable.get(0).capital();
        for (NodeProcess node : comparable.subList(1, comparable.size())) {
            assertEquals(expected, node.ledger(), node.diagnostic());
            assertEquals(expectedCapital, node.capital(), node.diagnostic());
        }
        String[] capitalRows = expectedCapital.split(",");
        assertEquals(seats, capitalRows.length,
                "multiprocess scenario lost a balance row");
        long stackTotal = java.util.Arrays.stream(capitalRows)
                .map(entry -> entry.substring(entry.indexOf('=') + 1)
                        .split("/"))
                .mapToLong(fields -> Long.parseLong(fields[0]))
                .sum();
        long buyinTotal = java.util.Arrays.stream(capitalRows)
                .map(entry -> entry.substring(entry.indexOf('=') + 1)
                        .split("/"))
                .mapToLong(fields -> Long.parseLong(fields[1]))
                .sum();
        assertEquals(buyinTotal, stackTotal,
                "final stacks must equal cumulative buy-ins");
    }

    private static void assertTerminalMisdealOutcome(
            List<NodeProcess> completed, NodeProcess host, String scenario) {
        assertTrue(host.contains("MISDEAL triggered:"),
                "Swing GOLD requires a terminal MISDEAL for " + scenario
                + "\n" + host.diagnostic());
        assertEquals(1, host.count("MISDEAL triggered:"),
                "terminal disruption must produce exactly one MISDEAL\n"
                + host.diagnostic());
        assertTrue(host.contains("RECOVERY: abortAndRecover engaged"),
                "terminal MISDEAL did not enter recoverable teardown\n"
                + host.diagnostic());
        for (NodeProcess node : completed) {
            if (scenario.equals("mixed-exit-crash")
                    && node.name.endsWith(":client1")) {
                assertTrue(node.contains("reason=EXITED"), node.diagnostic());
            } else {
                assertTrue(node.contains("reason=RECOVERABLE_STOP"),
                        node.diagnostic());
            }
        }
    }

    private static void assertMatchingCompletedHistory(
            List<NodeProcess> completed, String scenario, int hands) {
        List<NodeProcess> comparable = completed.stream()
                .filter(node -> !(scenario.equals("controlled-exit")
                        || scenario.equals("allin-controlled-exit"))
                        || !node.name.endsWith(":client1"))
                .toList();
        assertTrue(!comparable.isEmpty(),
                "scenario has no completed history witness: " + scenario);
        NodeProcess witness = comparable.get(0);
        assertTrue(witness.contains("CP_GDX_E2E_HANDS_COMPLETE hands="
                + hands + " durableHands=" + hands + " reason=COMPLETED"),
                witness.diagnostic());
        List<String> expectedConsensus = witness.consensusHistory();
        List<String> expectedBalances = witness.balanceHistory();
        assertEquals(hands, expectedConsensus.size(), witness.diagnostic());
        assertEquals(hands, expectedBalances.size(), witness.diagnostic());
        for (NodeProcess node : comparable.subList(1, comparable.size())) {
            assertTrue(node.contains("CP_GDX_E2E_HANDS_COMPLETE hands="
                    + hands + " durableHands=" + hands
                    + " reason=COMPLETED"), node.diagnostic());
            assertEquals(expectedConsensus, node.consensusHistory(),
                    "consensus history diverged in " + scenario + "\n"
                    + node.diagnostic());
            assertEquals(expectedBalances, node.balanceHistory(),
                    "balance history diverged in " + scenario + "\n"
                    + node.diagnostic());
        }
    }

    private static NodeProcess startNode(Path home, String role, String nick,
            int port, int clients, int bots, int hands, String scenario)
            throws IOException {
        return startNode(home, role, nick, port, clients, bots, hands,
                scenario, "initial");
    }

    private static NodeProcess startNode(Path home, String role, String nick,
            int port, int clients, int bots, int hands, String scenario,
            String phase) throws IOException {
        Files.createDirectories(home);
        String java = Path.of(System.getProperty("java.home"), "bin", "java")
                .toString();
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(java,
                "-Duser.home=" + home.toAbsolutePath(),
                "-Dcoronapoker.qa.handchainTrace="
                        + Boolean.getBoolean(
                                "coronapoker.qa.handchainTrace"),
                "-Dcoronapoker.qa.scenarioSeed="
                        + System.getProperty("coronapoker.qa.scenarioSeed",
                                System.getProperty("qa.sim.seed",
                                        "0x434f524f4e41514c")),
                "-cp", classpath,
                GdxMultiprocessNodeMain.class.getName(), role, nick,
                Integer.toString(port), Integer.toString(clients),
                Integer.toString(bots), Integer.toString(hands), scenario,
                phase);
        builder.redirectErrorStream(true);
        return new NodeProcess(role + ":" + nick, scenario, builder.start());
    }

    private static final class NodeProcess implements AutoCloseable {

        private final String name;
        private final String scenario;
        private final Process process;
        private final BufferedWriter input;
        private final List<String> output = Collections.synchronizedList(
                new ArrayList<>());
        private final CountDownLatch readerDone = new CountDownLatch(1);

        NodeProcess(String name, String scenario, Process process) {
            this.name = name;
            this.scenario = scenario;
            this.process = process;
            this.input = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            Thread reader = new Thread(() -> {
                try (BufferedReader lines = new BufferedReader(
                        new InputStreamReader(process.getInputStream(),
                                StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        output.add(line);
                        System.out.println("[" + name + "] " + line);
                    }
                } catch (IOException failure) {
                    output.add("reader failure: " + failure);
                } finally {
                    readerDone.countDown();
                }
            }, "gdx-e2e-output-" + name);
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(String command) throws IOException {
            input.write(command);
            input.newLine();
            input.flush();
        }

        void killAbruptly() {
            process.destroyForcibly();
        }

        boolean await(String marker, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                synchronized (output) {
                    if (output.stream().anyMatch(line -> line.contains(marker))) {
                        return true;
                    }
                }
                if (!process.isAlive()) {
                    readerDone.await(2, TimeUnit.SECONDS);
                    return false;
                }
                Thread.sleep(10L);
            }
            return false;
        }

        int awaitExit(Duration timeout) throws Exception {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return Integer.MIN_VALUE;
            }
            readerDone.await(2, TimeUnit.SECONDS);
            int exit = process.exitValue();
            if (exit == 0) {
                assertNoUnexpectedFailures();
            }
            return exit;
        }

        private void assertNoUnexpectedFailures() {
            for (String marker : ALWAYS_FATAL_OUTPUT) {
                assertTrue(!contains(marker),
                        "fatal output escaped the " + scenario
                        + " scenario oracle: " + marker + "\n"
                        + diagnostic());
            }
            assertTrue(!hasUnexpectedClientWriteFailure(),
                    "unexpected client write failure escaped the " + scenario
                    + " oracle\n" + diagnostic());
            if (!EXPECTED_MISDEAL_SCENARIOS.contains(scenario)) {
                assertTrue(!contains("MISDEAL triggered:"),
                        "unexpected MISDEAL in " + scenario + "\n"
                        + diagnostic());
                assertTrue(!contains("MANO ANULADA"),
                        "unexpected hand cancellation in " + scenario + "\n"
                        + diagnostic());
                assertTrue(!contains("QA dialog suppressed [Error"),
                        "unexpected error dialog in " + scenario + "\n"
                        + diagnostic());
            }
        }

        private boolean hasUnexpectedClientWriteFailure() {
            boolean cutArmed = false;
            boolean failureSeen = false;
            synchronized (output) {
                for (String line : output) {
                    if (line.contains("CP_GDX_E2E_SOCKET_DROP_REQUESTED")) {
                        if (cutArmed) return true;
                        cutArmed = true;
                        failureSeen = false;
                    }
                    if (line.contains("Client write failed")) {
                        if (!cutArmed || failureSeen) return true;
                        failureSeen = true;
                    }
                    if (line.contains("CP_GDX_E2E_RECONNECTED") && cutArmed) {
                        cutArmed = false;
                        failureSeen = false;
                    }
                }
            }
            return false;
        }

        List<String> consensusHistory() {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains(" verified: "))
                        .map(line -> line.substring(line.indexOf("Hand ")))
                        .toList();
            }
        }

        List<String> balanceHistory() {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains("Balance after hand "))
                        .map(NodeProcess::canonicalBalanceLine)
                        .toList();
            }
        }

        private static String canonicalBalanceLine(String line) {
            int handStart = line.indexOf("Balance after hand ");
            int rowsStart = line.indexOf(" -> ", handStart);
            if (handStart < 0 || rowsStart < 0) return line;
            String heading = line.substring(handStart, rowsStart);
            String rows = line.substring(rowsStart + 4);
            String canonical = java.util.Arrays.stream(rows.split("@"))
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("@"));
            return heading + " -> " + canonical;
        }

        void destroyForcibly() throws Exception {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            readerDone.await(2, TimeUnit.SECONDS);
        }

        String diagnostic() {
            synchronized (output) {
                return name + " exit="
                        + (process.isAlive() ? "alive" : process.exitValue())
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), output);
            }
        }

        String ledger() {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains("CP_GDX_E2E_LEDGER "))
                        .map(line -> line.substring(
                                line.indexOf("CP_GDX_E2E_LEDGER ")
                                + "CP_GDX_E2E_LEDGER ".length()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "missing ledger marker\n" + diagnostic()));
            }
        }

        String capital() {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains("CP_GDX_E2E_CAPITAL "))
                        .map(line -> line.substring(
                                line.indexOf("CP_GDX_E2E_CAPITAL ")
                                + "CP_GDX_E2E_CAPITAL ".length()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "missing capital marker\n" + diagnostic()));
            }
        }

        String valueAfter(String marker) {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains(marker))
                        .map(line -> line.substring(
                                line.indexOf(marker) + marker.length()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "missing marker " + marker + "\n"
                                + diagnostic()));
            }
        }

        boolean contains(String text) {
            synchronized (output) {
                return output.stream().anyMatch(line -> line.contains(text));
            }
        }

        long count(String text) {
            synchronized (output) {
                return output.stream().filter(line -> line.contains(text))
                        .count();
            }
        }

        @Override
        public void close() throws Exception {
            try {
                input.close();
            } catch (IOException ignored) {
            }
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
            }
            readerDone.await(2, TimeUnit.SECONDS);
        }
    }
}
