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
     * The only executable GDX scenario catalogue.
     *
     * <p>Every complete gameplay scenario belongs here exactly once. A scenario
     * can contain several complementary implementations (real independent
     * processes, native GDX controls and focused production-core flows), but
     * the certifier exposes them as one battery with one result. The Swing
     * reference above is only a historical parity invariant.</p>
     */
    static final Map<String, Set<String>> CERTIFICATION_SCENARIOS = Map.ofEntries(
            Map.entry("live-hot-join", Set.of(
                    "liveHotJoinWarmsWithPublicStateThenPlaysTheNextHand")),
            Map.entry("abrupt-exit", Set.of(
                    "abruptProcessExitLeavesIndependentGdxPeersRecoverable",
                    "abruptExitAbortsTheHandAndLeavesSurvivorsRecoverable")),
            Map.entry("controlled-exit", Set.of(
                    "controlledExitUsesIndependentGdxProcesses",
                    "nativeGdxExitConfirmationClosesRealNetworkTableWhilePaused",
                    "controlledExitDuringDecisionLetsTheRemainingTableFinishNormally",
                    "hostExitClosesBothNetworkGdxTablesDuringARealDecision")),
            Map.entry("dual-abrupt-exit", Set.of(
                    "dualAbruptExitMatchesTheSwingGoldScenarioAcrossGdxProcesses",
                    "dualAbruptExitRefundsTheTableAndLeavesTheWitnessRecoverable")),
            Map.entry("mixed-exit-crash", Set.of(
                    "mixedControlledExitAndCrashMatchesTheSwingGoldScenario",
                    "mixedControlledExitAndCrashPreserveTheWitnessAndLedger")),
            Map.entry("crash-rejoin-recover", Set.of(
                    "crashRejoinRecoveryRestartsTheSameGdxPeerAndCompletesHandTwo",
                    "crashedPeerRejoinsTheRecoverableGameAndCompletesTheNextHand")),
            Map.entry("allin-controlled-exit", Set.of(
                    "allInControlledExitMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "allInControlledExitRetainsTheProofAndSettlesTheHostTable")),
            Map.entry("allin-reconnect", Set.of(
                    "allInReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "allInReconnectPreservesAcceptedActionAndSettlesEveryGdxTable")),
            Map.entry("allin-abrupt-exit", Set.of(
                    "allInAbruptExitMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "allInAbruptExitRefundsTheHandAndLeavesTheWitnessRecoverable")),
            Map.entry("pause-resume", Set.of(
                    "pauseResumePreservesTheDecisionAcrossIndependentGdxProcesses",
                    "nativeGdxPauseResumeCompletesARealTwoHumanHand",
                    "pauseResumeScenarioPreservesTheDecisionAndCompletesTwoHands")),
            Map.entry("reconnect-midhand", Set.of(
                    "reconnectMidHandReplacesTheSocketAcrossIndependentGdxProcesses",
                    "reconnectMidHandPreservesBothGdxTablesAndCompletesTheGame")),
            Map.entry("reconnect-twice", Set.of(
                    "reconnectTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "reconnectTwiceUsesDifferentGdxPeersAndCompletesThreeHands")),
            Map.entry("reconnect-storm", Set.of(
                    "reconnectStormMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "reconnectStormReplacesFreshSocketTwiceAndAnotherPeerNextHand")),
            Map.entry("dual-reconnect", Set.of(
                    "dualReconnectMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "dualReconnectRecoversTwoPeersTogetherAndSettlesEveryGdxTable")),
            Map.entry("host-channel-flap", Set.of(
                    "hostChannelFlapMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "hostChannelFlapRecoversEveryClientAndSettlesEveryGdxTable")),
            Map.entry("reconnect-every-street", Set.of(
                    "reconnectEveryStreetMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "reconnectEveryStreetCompletesFourHandsWithIdenticalSettlements")),
            Map.entry("reconnect-force-recover", Set.of(
                    "reconnectAndForceRecoveryConvergeAcrossIndependentGdxProcesses",
                    "reconnectOverlappingForceRecoveryConvergesAcrossEveryGdxPeer")),
            Map.entry("force-recover", Set.of(
                    "forceRecoveryRebuildsBothGdxProcessesAndCompletesTwoHands",
                    "nativeGdxRecoveryAppliesTheRecordedActionInCoreAndClosesItsOverlay",
                    "forceRecoverRebuildsTheNetworkTableAndCompletesTwoHands")),
            Map.entry("double-force-recover", Set.of(
                    "doubleForceRecoveryRebuildsBothGdxProcessesAtHandsOneAndThree",
                    "doubleForceRecoverRebuildsHandsOneAndThreeAndCompletesFourHands")),
            Map.entry("force-recover-add-client", Set.of(
                    "forceRecoveryAdmitsNewIndependentGdxProcessForFreshSecondHand",
                    "forceRecoveryAdmitsNewGdxClientForFreshSecondHand")),
            Map.entry("force-recover-add-two", Set.of(
                    "forceRecoveryAdmitsTwoNewIndependentGdxProcessesForSecondHand",
                    "forceRecoveryAdmitsTwoNewGdxClientsForFreshSecondHand")),
            Map.entry("force-recover-swap-client", Set.of(
                    "forceRecoveryReplacesMissingIndependentGdxProcessForSecondHand",
                    "forceRecoveryReplacesMissingClientAndStartsFreshSecondHand")),
            Map.entry("spectator-rebuy-cycle", Set.of(
                    "spectatorRebuyCycleReturnsBustedIndependentGdxProcessToPlay",
                    "nativeGdxSpectatorChoiceReleasesTheRealNetworkDealerEvenWhenAudioCallbackIsLost",
                    "bustedGdxHumanSpectatesThenRebuysAndReturnsToTheActiveRing")),
            Map.entry("spectator-recovery-mix", Set.of(
                    "spectatorsSurviveRecoveryRebuyAndTwoNewIndependentGdxProcesses",
                    "spectatorsSurviveRecoveryRebuyAndTwoNewHumansJoining")),
            Map.entry("bot-bust-recover-regrow", Set.of(
                    "bustedBotRegrowsAcrossIndependentGdxProcessesAfterRecovery",
                    "bustedBotRegrowsAtTheRecoveredHandBoundary")),
            Map.entry("bot-bust-recover-drop", Set.of(
                    "bustedBotDropsAcrossIndependentGdxProcessesAfterRecovery",
                    "bustedBotDropsFromTheRecoveredActiveRing")),
            Map.entry("human-bust-exit-rejoin-rebuy", Set.of(
                    "bustedHumanExitsRejoinsWithSameIdentityAcrossIndependentGdxProcesses",
                    "bustedHumanExitsRejoinsWithSameIdentityAndRebuysAfterRecovery")),
            Map.entry("spectator-double-recovery-crash-mix", Set.of(
                    "spectatorDoubleRecoveryCrashMixRunsAcrossIndependentGdxProcesses",
                    "spectatorsAndNewcomersSurviveTwoRecoveriesAndARealClientCrash")),
            Map.entry("transport-chaos", Set.of(
                    "transportChaosConvergesAcrossIndependentGdxProcesses",
                    "transportChaosConvergesAfterDualCutRelapsePauseRecoveryAndLaterCut")),
            Map.entry("lifecycle-chaos", Set.of(
                    "lifecycleChaosConvergesAcrossIndependentGdxProcesses",
                    "lifecycleChaosConvergesAcrossReconnectPauseAndTwoRecoveryCycles")),
            Map.entry("rit-network-cut", Set.of(
                    "runItTwiceVoteSurvivesNetworkCutAcrossGdxProcesses",
                    "nativeGdxRunItTwiceVoteSurvivesReconnectBeforeTheDelayedFinalVote")),
            Map.entry("straddle-network-cut", Set.of(
                    "straddleAcceptedResponseSurvivesNetworkCutAcrossGdxProcesses",
                    "nativeGdxStraddleAcceptedResponseSurvivesReconnectBeforeDeferredPocketDelivery")),
            Map.entry("normal", Set.of(
                    "normalHeadsUpMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalSoakMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalFullMixedMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalFullHumanMatchesTheSwingGoldTopologyAcrossGdxProcesses",
                    "normalSupportingTopologyRunsAsIndependentGdxProcesses",
                    "nativeGdxCheckCallControlsCompleteARealTwoHumanHand",
                    "nativeGdxFoldedLocalStillSeesRemoteMonteCarloRevealsAndShowdownResults",
                    "uncontestedWinnerIsPublishedBeforeTheBetweenHandsPayout",
                    "normalHeadsUpMatchesTheFastSwingTopology",
                    "normalSoakMatchesTheFastSwingTopology",
                    "normalFullMixedMatchesTheFastSwingTopology",
                    "normalFullHumanMatchesTheFastSwingTopology",
                    "normalScenarioCompletesOneHandWithOnePeerAndTwoBots")),
            Map.entry("raise-mix", Set.of(
                    "raiseMixMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxRaiseMixUsesRealHumanRaiseControlsAndSettlesTenHands")),
            Map.entry("allin-single-board", Set.of(
                    "allInSingleBoardMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxAllInButtonArmsBeforeSubmittingTheRealCommand",
                    "allInSingleBoardCompletesWithOneBoardAndConservedBalances",
                    "networkAllInCinematicBlocksTheFollowingTurnInBothGdxProjections")),
            Map.entry("allin-rebuy", Set.of(
                    "allInRebuyMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxManualRebuyKeepsBothNetworkTablesAliveForTheNextHand",
                    "allInRebuyCompletesFiveHandsAndCarriesARebuyForward")),
            Map.entry("allin-rit", Set.of(
                    "allInRunItTwiceMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxAllInRunItTwiceCompletesBothBoardsAndConservesBalances")),
            Map.entry("straddle-post", Set.of(
                    "straddlePostMatchesTheSwingGoldSequenceAcrossGdxProcesses",
                    "nativeGdxStraddlePostRotatesAllThreeHumansAcrossThreeHands")),
            Map.entry("turn-timeout", Set.of(
                    "networkTimeoutStopsTheGdxTimerAndAdvancesBothTables")),
            Map.entry("live-rules-last-hand", Set.of(
                    "hostLiveRulesAndLastHandReachBothNetworkGdxTables")),
            Map.entry("paused-exit", Set.of(
                    "hostExitWhilePausedCannotLeaveEitherNetworkGdxTableBlocked")),
            Map.entry("rabbit-hunting", Set.of(
                    "gdxRabbitRequestIsAuthorizedChargedAndRevealedAcrossNetwork")),
            Map.entry("iwtsth", Set.of(
                    "gdxIwtsthCandidateRequestsAndRevealsTheMuckedNetworkHand",
                    "nativeGdxIwtsthOffersAndRevealsAMuckedBotLikeSwing")),
            Map.entry("blind-increase", Set.of(
                    "realGdxHandsAdvanceBlindsUpdateBothHudProjectionsAndPlayTheGong")));

    private GdxScenarioContract() {
    }
}
