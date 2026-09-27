package com.tonikelope.coronapoker.gdx;

import java.util.Map;
import java.util.Set;

/**
 * GDX-owned executable mapping of the historical Swing GOLD scenario catalogue.
 *
 * <p>The immutable baseline is stored in
 * {@code tools/qa/reference/swing-gold-scenarios.tsv}. This executable mapping
 * deliberately lives in the GDX test tree so coverage can grow without
 * weakening or silently dropping a historical GOLD contract.</p>
 */
final class GdxScenarioContract {

    static final Set<String> SWING_REFERENCE = Set.of(
            "abrupt-exit",
            "controlled-exit",
            "dual-abrupt-exit",
            "mixed-exit-crash",
            "crash-rejoin-recover",
            "allin-controlled-exit",
            "allin-reconnect",
            "allin-abrupt-exit",
            "pause-resume",
            "reconnect-midhand",
            "reconnect-twice",
            "reconnect-storm",
            "dual-reconnect",
            "host-channel-flap",
            "reconnect-every-street",
            "reconnect-force-recover",
            "force-recover",
            "double-force-recover",
            "force-recover-add-client",
            "force-recover-add-two",
            "force-recover-swap-client",
            "spectator-rebuy-cycle",
            "spectator-recovery-mix",
            "bot-bust-recover-regrow",
            "bot-bust-recover-drop",
            "human-bust-exit-rejoin-rebuy",
            "spectator-double-recovery-crash-mix",
            "transport-chaos",
            "lifecycle-chaos",
            "rit-network-cut",
            "straddle-network-cut",
            "normal",
            "raise-mix",
            "allin-single-board",
            "allin-rebuy",
            "allin-rit",
            "straddle-post");

    /**
     * The release-certifying port of the Swing GOLD catalogue. Every entry is
     * one real multi-process test: independent JVMs, loopback sockets and the
     * production GDX table/session wiring. In-process scenario simulations are
     * deliberately excluded from this map and can never satisfy GOLD parity.
     */
    static final Map<String, Set<String>> SWING_GOLD_MULTIPROCESS_TESTS
            = Map.ofEntries(
            Map.entry("abrupt-exit", Set.of(
                    "abruptProcessExitLeavesIndependentGdxPeersRecoverable")),
            Map.entry("controlled-exit", Set.of(
                    "controlledExitUsesIndependentGdxProcesses")),
            Map.entry("dual-abrupt-exit", Set.of(
                    "dualAbruptExitMatchesTheSwingGoldScenarioAcrossGdxProcesses")),
            Map.entry("mixed-exit-crash", Set.of(
                    "mixedControlledExitAndCrashMatchesTheSwingGoldScenario")),
            Map.entry("crash-rejoin-recover", Set.of(
                    "crashRejoinRecoveryRestartsTheSameGdxPeerAndCompletesHandTwo")),
            Map.entry("allin-controlled-exit", Set.of(
                    "allInControlledExitMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-reconnect", Set.of(
                    "allInReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-abrupt-exit", Set.of(
                    "allInAbruptExitMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("pause-resume", Set.of(
                    "pauseResumePreservesTheDecisionAcrossIndependentGdxProcesses")),
            Map.entry("reconnect-midhand", Set.of(
                    "reconnectMidHandReplacesTheSocketAcrossIndependentGdxProcesses")),
            Map.entry("reconnect-twice", Set.of(
                    "reconnectTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("reconnect-storm", Set.of(
                    "reconnectStormMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("dual-reconnect", Set.of(
                    "dualReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("host-channel-flap", Set.of(
                    "hostChannelFlapMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("reconnect-every-street", Set.of(
                    "reconnectEveryStreetMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("reconnect-force-recover", Set.of(
                    "reconnectAndForceRecoveryConvergeAcrossIndependentGdxProcesses")),
            Map.entry("force-recover", Set.of(
                    "forceRecoveryRebuildsBothGdxProcessesAndCompletesTwoHands")),
            Map.entry("double-force-recover", Set.of(
                    "doubleForceRecoveryRebuildsBothGdxProcessesAtHandsOneAndThree")),
            Map.entry("force-recover-add-client", Set.of(
                    "forceRecoveryAdmitsNewIndependentGdxProcessForFreshSecondHand")),
            Map.entry("force-recover-add-two", Set.of(
                    "forceRecoveryAdmitsTwoNewIndependentGdxProcessesForSecondHand")),
            Map.entry("force-recover-swap-client", Set.of(
                    "forceRecoveryReplacesMissingIndependentGdxProcessForSecondHand")),
            Map.entry("spectator-rebuy-cycle", Set.of(
                    "spectatorRebuyCycleReturnsBustedIndependentGdxProcessToPlay")),
            Map.entry("spectator-recovery-mix", Set.of(
                    "spectatorsSurviveRecoveryRebuyAndTwoNewIndependentGdxProcesses")),
            Map.entry("bot-bust-recover-regrow", Set.of(
                    "bustedBotRegrowsAcrossIndependentGdxProcessesAfterRecovery")),
            Map.entry("bot-bust-recover-drop", Set.of(
                    "bustedBotDropsAcrossIndependentGdxProcessesAfterRecovery")),
            Map.entry("human-bust-exit-rejoin-rebuy", Set.of(
                    "bustedHumanExitsRejoinsWithSameIdentityAcrossIndependentGdxProcesses")),
            Map.entry("spectator-double-recovery-crash-mix", Set.of(
                    "spectatorDoubleRecoveryCrashMixRunsAcrossIndependentGdxProcesses")),
            Map.entry("transport-chaos", Set.of(
                    "transportChaosConvergesAcrossIndependentGdxProcesses")),
            Map.entry("lifecycle-chaos", Set.of(
                    "lifecycleChaosConvergesAcrossIndependentGdxProcesses")),
            Map.entry("rit-network-cut", Set.of(
                    "runItTwiceVoteSurvivesNetworkCutAcrossGdxProcesses")),
            Map.entry("straddle-network-cut", Set.of(
                    "straddleAcceptedResponseSurvivesNetworkCutAcrossGdxProcesses")),
            Map.entry("normal", Set.of(
                    "normalHeadsUpMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalSoakMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalFullMixedMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalFullHumanMatchesTheSwingGoldTopologyAcrossGdxProcesses")),
            Map.entry("raise-mix", Set.of(
                    "raiseMixMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-single-board", Set.of(
                    "allInSingleBoardMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-rebuy", Set.of(
                    "allInRebuyMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-rit", Set.of(
                    "allInRunItTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("straddle-post", Set.of(
                    "straddlePostMatchesTheSwingGoldSequenceAcrossGdxProcesses")));

    /** Product-table/UI checks that supplement, but never replace, GOLD. */
    static final Map<String, Set<String>> NATIVE_GDX_UI_TESTS = Map.ofEntries(
            Map.entry("normal", Set.of(
                    "nativeGdxCheckCallControlsCompleteARealTwoHumanHand",
                    "nativeGdxFoldedLocalStillSeesRemoteMonteCarloRevealsAndShowdownResults")),
            Map.entry("raise-mix", Set.of(
                    "nativeGdxRaiseMixUsesRealHumanRaiseControlsAndSettlesTenHands")),
            Map.entry("allin-single-board", Set.of(
                    "nativeGdxAllInButtonArmsBeforeSubmittingTheRealCommand")),
            Map.entry("allin-rit", Set.of(
                    "nativeGdxAllInRunItTwiceCompletesBothBoardsAndConservesBalances")),
            Map.entry("allin-rebuy", Set.of(
                    "nativeGdxManualRebuyKeepsBothNetworkTablesAliveForTheNextHand")),
            Map.entry("straddle-post", Set.of(
                    "nativeGdxStraddlePostRotatesAllThreeHumansAcrossThreeHands")),
            Map.entry("pause-resume", Set.of(
                    "nativeGdxPauseResumeCompletesARealTwoHumanHand")),
            Map.entry("controlled-exit", Set.of(
                    "nativeGdxExitConfirmationClosesRealNetworkTableWhilePaused")),
            Map.entry("spectator-rebuy-cycle", Set.of(
                    "nativeGdxSpectatorChoiceReleasesTheRealNetworkDealerEvenWhenAudioCallbackIsLost")),
            Map.entry("force-recover", Set.of(
                    "nativeGdxRecoveryAppliesTheRecordedActionInCoreAndClosesItsOverlay")),
            Map.entry("rit-network-cut", Set.of(
                    "nativeGdxRunItTwiceVoteSurvivesReconnectBeforeTheDelayedFinalVote")),
            Map.entry("straddle-network-cut", Set.of(
                    "nativeGdxStraddleAcceptedResponseSurvivesReconnectBeforeDeferredPocketDelivery")));

    /**
     * Useful GDX network coverage that predates the strict one-for-one port.
     * These tests never count as Swing-scenario certification.
     */
    static final Map<String, Set<String>> SUPPORTING_NETWORK_TESTS = Map.ofEntries(
            Map.entry("normal", Set.of(
                    "nativeGdxPauseResumeCompletesARealTwoHumanHand",
                    "normalScenarioCompletesOneHandWithOnePeerAndTwoBots")),
            Map.entry("pause-resume", Set.of(
                    "nativeGdxPauseResumeCompletesARealTwoHumanHand")),
            Map.entry("turn-timeout", Set.of(
                    "networkTimeoutStopsTheGdxTimerAndAdvancesBothTables")),
            Map.entry("allin-single-board", Set.of(
                    "networkAllInCinematicBlocksTheFollowingTurnInBothGdxProjections")),
            Map.entry("allin-rebuy", Set.of(
                    "nativeGdxManualRebuyKeepsBothNetworkTablesAliveForTheNextHand")),
            Map.entry("live-rules-last-hand", Set.of(
                    "hostLiveRulesAndLastHandReachBothNetworkGdxTables")),
            Map.entry("controlled-exit", Set.of(
                    "hostExitClosesBothNetworkGdxTablesDuringARealDecision")),
            Map.entry("paused-exit", Set.of(
                    "hostExitWhilePausedCannotLeaveEitherNetworkGdxTableBlocked")));

    /**
     * Auxiliary lower-layer and native regressions grouped by their historical
     * Swing scenario. They improve diagnosis and projection coverage, but are
     * not read by the certifier and can never substitute for the official
     * independent-process GOLD mapping above.
     */
    static final Map<String, Set<String>> AUXILIARY_HOMOLOGUE_TESTS = Map.ofEntries(
            Map.entry("abrupt-exit", Set.of(
                    "abruptExitAbortsTheHandAndLeavesSurvivorsRecoverable",
                    "abruptProcessExitLeavesIndependentGdxPeersRecoverable")),
            Map.entry("dual-abrupt-exit", Set.of(
                    "dualAbruptExitRefundsTheTableAndLeavesTheWitnessRecoverable",
                    "dualAbruptExitMatchesTheSwingGoldScenarioAcrossGdxProcesses")),
            Map.entry("mixed-exit-crash", Set.of(
                    "mixedControlledExitAndCrashPreserveTheWitnessAndLedger",
                    "mixedControlledExitAndCrashMatchesTheSwingGoldScenario")),
            Map.entry("crash-rejoin-recover", Set.of(
                    "crashedPeerRejoinsTheRecoverableGameAndCompletesTheNextHand",
                    "crashRejoinRecoveryRestartsTheSameGdxPeerAndCompletesHandTwo")),
            Map.entry("force-recover", Set.of(
                    "forceRecoverRebuildsTheNetworkTableAndCompletesTwoHands",
                    "forceRecoveryRebuildsBothGdxProcessesAndCompletesTwoHands",
                    "nativeGdxRecoveryAppliesTheRecordedActionInCoreAndClosesItsOverlay")),
            Map.entry("double-force-recover", Set.of(
                    "doubleForceRecoverRebuildsHandsOneAndThreeAndCompletesFourHands",
                    "doubleForceRecoveryRebuildsBothGdxProcessesAtHandsOneAndThree")),
            Map.entry("reconnect-force-recover", Set.of(
                    "reconnectOverlappingForceRecoveryConvergesAcrossEveryGdxPeer",
                    "reconnectAndForceRecoveryConvergeAcrossIndependentGdxProcesses")),
            Map.entry("force-recover-add-client", Set.of(
                    "forceRecoveryAdmitsNewGdxClientForFreshSecondHand",
                    "forceRecoveryAdmitsNewIndependentGdxProcessForFreshSecondHand")),
            Map.entry("force-recover-add-two", Set.of(
                    "forceRecoveryAdmitsTwoNewGdxClientsForFreshSecondHand",
                    "forceRecoveryAdmitsTwoNewIndependentGdxProcessesForSecondHand")),
            Map.entry("force-recover-swap-client", Set.of(
                    "forceRecoveryReplacesMissingClientAndStartsFreshSecondHand",
                    "forceRecoveryReplacesMissingIndependentGdxProcessForSecondHand")),
            Map.entry("spectator-rebuy-cycle", Set.of(
                    "bustedGdxHumanSpectatesThenRebuysAndReturnsToTheActiveRing",
                    "nativeGdxSpectatorChoiceReleasesTheRealNetworkDealerEvenWhenAudioCallbackIsLost",
                    "spectatorRebuyCycleReturnsBustedIndependentGdxProcessToPlay")),
            Map.entry("spectator-recovery-mix", Set.of(
                    "spectatorsSurviveRecoveryRebuyAndTwoNewHumansJoining",
                    "spectatorsSurviveRecoveryRebuyAndTwoNewIndependentGdxProcesses")),
            Map.entry("bot-bust-recover-regrow", Set.of(
                    "bustedBotRegrowsAtTheRecoveredHandBoundary",
                    "bustedBotRegrowsAcrossIndependentGdxProcessesAfterRecovery")),
            Map.entry("bot-bust-recover-drop", Set.of(
                    "bustedBotDropsFromTheRecoveredActiveRing",
                    "bustedBotDropsAcrossIndependentGdxProcessesAfterRecovery")),
            Map.entry("human-bust-exit-rejoin-rebuy", Set.of(
                    "bustedHumanExitsRejoinsWithSameIdentityAndRebuysAfterRecovery",
                    "bustedHumanExitsRejoinsWithSameIdentityAcrossIndependentGdxProcesses")),
            Map.entry("spectator-double-recovery-crash-mix", Set.of(
                    "spectatorsAndNewcomersSurviveTwoRecoveriesAndARealClientCrash",
                    "spectatorDoubleRecoveryCrashMixRunsAcrossIndependentGdxProcesses")),
            Map.entry("transport-chaos", Set.of(
                    "transportChaosConvergesAfterDualCutRelapsePauseRecoveryAndLaterCut",
                    "transportChaosConvergesAcrossIndependentGdxProcesses")),
            Map.entry("lifecycle-chaos", Set.of(
                    "lifecycleChaosConvergesAcrossReconnectPauseAndTwoRecoveryCycles",
                    "lifecycleChaosConvergesAcrossIndependentGdxProcesses")),
            Map.entry("rit-network-cut", Set.of(
                    "runItTwiceVoteSurvivesNetworkCutAcrossGdxProcesses",
                    "nativeGdxRunItTwiceVoteSurvivesReconnectBeforeTheDelayedFinalVote")),
            Map.entry("straddle-network-cut", Set.of(
                    "straddleAcceptedResponseSurvivesNetworkCutAcrossGdxProcesses",
                    "nativeGdxStraddleAcceptedResponseSurvivesReconnectBeforeDeferredPocketDelivery")),
            Map.entry("normal", Set.of(
                    "nativeGdxCheckCallControlsCompleteARealTwoHumanHand",
                    "nativeGdxFoldedLocalStillSeesRemoteMonteCarloRevealsAndShowdownResults",
                    "normalHeadsUpMatchesTheFastSwingTopology",
                    "normalSoakMatchesTheFastSwingTopology",
                    "normalFullMixedMatchesTheFastSwingTopology",
                    "normalFullHumanMatchesTheFastSwingTopology",
                    "normalHeadsUpMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalSoakMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalFullMixedMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalFullHumanMatchesTheSwingGoldTopologyAcrossGdxProcesses")),
            Map.entry("raise-mix", Set.of(
                    "nativeGdxRaiseMixUsesRealHumanRaiseControlsAndSettlesTenHands",
                    "raiseMixMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-controlled-exit", Set.of(
                    "allInControlledExitRetainsTheProofAndSettlesTheHostTable",
                    "allInControlledExitMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-abrupt-exit", Set.of(
                    "allInAbruptExitRefundsTheHandAndLeavesTheWitnessRecoverable",
                    "allInAbruptExitMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-single-board", Set.of(
                    "allInSingleBoardCompletesWithOneBoardAndConservedBalances",
                    "nativeGdxAllInButtonArmsBeforeSubmittingTheRealCommand",
                    "allInSingleBoardMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-rit", Set.of(
                    "allInRunItTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxAllInRunItTwiceCompletesBothBoardsAndConservesBalances")),
            Map.entry("allin-rebuy", Set.of(
                    "allInRebuyCompletesFiveHandsAndCarriesARebuyForward",
                    "nativeGdxManualRebuyKeepsBothNetworkTablesAliveForTheNextHand",
                    "allInRebuyMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("straddle-post", Set.of(
                    "straddlePostMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxStraddlePostRotatesAllThreeHumansAcrossThreeHands")),
            Map.entry("pause-resume", Set.of(
                    "pauseResumeScenarioPreservesTheDecisionAndCompletesTwoHands",
                    "nativeGdxPauseResumeCompletesARealTwoHumanHand",
                    "pauseResumePreservesTheDecisionAcrossIndependentGdxProcesses")),
            Map.entry("controlled-exit", Set.of(
                    "controlledExitDuringDecisionLetsTheRemainingTableFinishNormally",
                    "nativeGdxExitConfirmationClosesRealNetworkTableWhilePaused",
                    "controlledExitUsesIndependentGdxProcesses")),
            Map.entry("reconnect-midhand", Set.of(
                    "reconnectMidHandPreservesBothGdxTablesAndCompletesTheGame",
                    "reconnectMidHandReplacesTheSocketAcrossIndependentGdxProcesses")),
            Map.entry("reconnect-twice", Set.of(
                    "reconnectTwiceUsesDifferentGdxPeersAndCompletesThreeHands",
                    "reconnectTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("reconnect-storm", Set.of(
                    "reconnectStormReplacesFreshSocketTwiceAndAnotherPeerNextHand",
                    "reconnectStormMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("dual-reconnect", Set.of(
                    "dualReconnectRecoversTwoPeersTogetherAndSettlesEveryGdxTable",
                    "dualReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("host-channel-flap", Set.of(
                    "hostChannelFlapRecoversEveryClientAndSettlesEveryGdxTable",
                    "hostChannelFlapMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("reconnect-every-street", Set.of(
                    "reconnectEveryStreetCompletesFourHandsWithIdenticalSettlements",
                    "reconnectEveryStreetMatchesTheSwingGoldSequenceAcrossGdxProcesses")),
            Map.entry("allin-reconnect", Set.of(
                    "allInReconnectPreservesAcceptedActionAndSettlesEveryGdxTable",
                    "allInReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses")));

    /**
     * Product scenarios added after the Swing baseline was frozen. They are
     * first-class certification scenarios, but must never be inserted into the
     * historical one-for-one mapping above.
     */
    static final Map<String, Set<String>> GDX_ONLY_SCENARIOS = Map.ofEntries(
            Map.entry("turn-timeout", Set.of(
                    "networkTimeoutStopsTheGdxTimerAndAdvancesBothTables")),
            Map.entry("live-rules-last-hand", Set.of(
                    "hostLiveRulesAndLastHandReachBothNetworkGdxTables")),
            Map.entry("paused-exit", Set.of(
                    "hostExitWhilePausedCannotLeaveEitherNetworkGdxTableBlocked")),
            Map.entry("rabbit-hunting", Set.of(
                    "gdxRabbitRequestIsAuthorizedChargedAndRevealedAcrossNetwork")));

    private GdxScenarioContract() {
    }
}
