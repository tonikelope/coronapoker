package com.tonikelope.coronapoker.gdx;

import java.util.Map;
import java.util.Set;

/**
 * GDX-owned copy of the production-loopback scenario catalogue.
 *
 * <p>The Swing suite is the immutable behavioural reference.  This catalogue
 * deliberately lives in the GDX test tree so GDX coverage can grow without
 * changing the reference suite or weakening one of its contracts.</p>
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
     * Exact Swing-contract homologues for real game/network behaviour and the
     * GDX event projection. They do not, by themselves, certify OpenGL drawing
     * or pointer geometry in {@link CoronaPokerGdxTable}; scenario-specific
     * production-table wiring tests must supplement them where UI interaction
     * can affect the dealer lifecycle.
     */
    static final Map<String, Set<String>> STRICT_HOMOLOGUE_TESTS = Map.ofEntries(
            Map.entry("abrupt-exit", Set.of(
                    "abruptExitAbortsTheHandAndLeavesSurvivorsRecoverable")),
            Map.entry("dual-abrupt-exit", Set.of(
                    "dualAbruptExitRefundsTheTableAndLeavesTheWitnessRecoverable")),
            Map.entry("mixed-exit-crash", Set.of(
                    "mixedControlledExitAndCrashPreserveTheWitnessAndLedger")),
            Map.entry("crash-rejoin-recover", Set.of(
                    "crashedPeerRejoinsTheRecoverableGameAndCompletesTheNextHand")),
            Map.entry("force-recover", Set.of(
                    "forceRecoverRebuildsTheNetworkTableAndCompletesTwoHands",
                    "nativeGdxRecoveryReplaysTheRecordedLocalActionAndClosesItsOverlay")),
            Map.entry("double-force-recover", Set.of(
                    "doubleForceRecoverRebuildsHandsOneAndThreeAndCompletesFourHands")),
            Map.entry("reconnect-force-recover", Set.of(
                    "reconnectOverlappingForceRecoveryConvergesAcrossEveryGdxPeer")),
            Map.entry("force-recover-add-client", Set.of(
                    "forceRecoveryAdmitsNewGdxClientForFreshSecondHand")),
            Map.entry("force-recover-add-two", Set.of(
                    "forceRecoveryAdmitsTwoNewGdxClientsForFreshSecondHand")),
            Map.entry("force-recover-swap-client", Set.of(
                    "forceRecoveryReplacesMissingClientAndStartsFreshSecondHand")),
            Map.entry("spectator-rebuy-cycle", Set.of(
                    "bustedGdxHumanSpectatesThenRebuysAndReturnsToTheActiveRing",
                    "nativeGdxSpectatorChoiceReleasesTheRealNetworkDealerEvenWhenAudioCallbackIsLost")),
            Map.entry("spectator-recovery-mix", Set.of(
                    "spectatorsSurviveRecoveryRebuyAndTwoNewHumansJoining")),
            Map.entry("bot-bust-recover-regrow", Set.of(
                    "bustedBotRegrowsAtTheRecoveredHandBoundary")),
            Map.entry("bot-bust-recover-drop", Set.of(
                    "bustedBotDropsFromTheRecoveredActiveRing")),
            Map.entry("human-bust-exit-rejoin-rebuy", Set.of(
                    "bustedHumanExitsRejoinsWithSameIdentityAndRebuysAfterRecovery")),
            Map.entry("spectator-double-recovery-crash-mix", Set.of(
                    "spectatorsAndNewcomersSurviveTwoRecoveriesAndARealClientCrash")),
            Map.entry("transport-chaos", Set.of(
                    "transportChaosConvergesAfterDualCutRelapsePauseRecoveryAndLaterCut")),
            Map.entry("lifecycle-chaos", Set.of(
                    "lifecycleChaosConvergesAcrossReconnectPauseAndTwoRecoveryCycles")),
            Map.entry("rit-network-cut", Set.of(
                    "nativeGdxRunItTwiceVoteSurvivesReconnectBeforeTheDelayedFinalVote")),
            Map.entry("straddle-network-cut", Set.of(
                    "nativeGdxStraddleAcceptedResponseSurvivesReconnectBeforeDeferredPocketDelivery")),
            Map.entry("normal", Set.of(
                    "nativeGdxCheckCallControlsCompleteARealTwoHumanHand",
                    "nativeGdxFoldedLocalStillSeesRemoteMonteCarloRevealsAndShowdownResults",
                    "normalHeadsUpMatchesTheFastSwingTopology",
                    "normalSoakMatchesTheFastSwingTopology",
                    "normalFullMixedMatchesTheFastSwingTopology",
                    "normalFullHumanMatchesTheFastSwingTopology")),
            Map.entry("raise-mix", Set.of(
                    "nativeGdxRaiseMixUsesRealHumanRaiseControlsAndSettlesTenHands")),
            Map.entry("allin-controlled-exit", Set.of(
                    "allInControlledExitRetainsTheProofAndSettlesTheHostTable")),
            Map.entry("allin-abrupt-exit", Set.of(
                    "allInAbruptExitRefundsTheHandAndLeavesTheWitnessRecoverable")),
            Map.entry("allin-single-board", Set.of(
                    "allInSingleBoardCompletesWithOneBoardAndConservedBalances",
                    "nativeGdxAllInButtonArmsBeforeSubmittingTheRealCommand")),
            Map.entry("allin-rit", Set.of(
                    "nativeGdxAllInRunItTwiceCompletesBothBoardsAndConservesBalances")),
            Map.entry("allin-rebuy", Set.of(
                    "allInRebuyCompletesFiveHandsAndCarriesARebuyForward",
                    "nativeGdxManualRebuyKeepsBothNetworkTablesAliveForTheNextHand")),
            Map.entry("straddle-post", Set.of(
                    "nativeGdxStraddlePostRotatesAllThreeHumansAcrossThreeHands")),
            Map.entry("pause-resume", Set.of(
                    "pauseResumeScenarioPreservesTheDecisionAndCompletesTwoHands",
                    "nativeGdxPauseResumeCompletesARealTwoHumanHand")),
            Map.entry("controlled-exit", Set.of(
                    "controlledExitDuringDecisionLetsTheRemainingTableFinishNormally",
                    "nativeGdxExitConfirmationClosesRealNetworkTableWhilePaused")),
            Map.entry("reconnect-midhand", Set.of(
                    "reconnectMidHandPreservesBothGdxTablesAndCompletesTheGame")),
            Map.entry("reconnect-twice", Set.of(
                    "reconnectTwiceUsesDifferentGdxPeersAndCompletesThreeHands")),
            Map.entry("reconnect-storm", Set.of(
                    "reconnectStormReplacesFreshSocketTwiceAndAnotherPeerNextHand")),
            Map.entry("dual-reconnect", Set.of(
                    "dualReconnectRecoversTwoPeersTogetherAndSettlesEveryGdxTable")),
            Map.entry("host-channel-flap", Set.of(
                    "hostChannelFlapRecoversEveryClientAndSettlesEveryGdxTable")),
            Map.entry("reconnect-every-street", Set.of(
                    "reconnectEveryStreetCompletesFourHandsWithIdenticalSettlements")),
            Map.entry("allin-reconnect", Set.of(
                    "allInReconnectPreservesAcceptedActionAndSettlesEveryGdxTable")));

    private GdxScenarioContract() {
    }
}
