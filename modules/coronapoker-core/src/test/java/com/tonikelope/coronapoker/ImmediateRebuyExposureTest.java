package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.CorePlayerController;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImmediateRebuyExposureTest {

    @Test
    void includesEveryChipAlreadyInvestedInTheCurrentHand() {
        assertEquals(10d, Crupier.rebuyTableExposure(7.9d, 2.1d, 0d));
        assertEquals(10d, Crupier.rebuyTableExposure(0d, 10d, 0d));
        assertEquals(9.9d, Crupier.rebuyTableExposure(9.8d, 0.1d, 0d));
        assertEquals(10.70d, Crupier.rebuyTableExposure(0d, 10d, 10.70d));
        assertEquals(10.70d, Crupier.rebuyTableExposure(0d, 0d, 10.70d));
    }

    @Test
    void neverCreatesNegativeExposureFromInvalidComponents() {
        assertEquals(4d, Crupier.rebuyTableExposure(-2d, 4d, -1d));
        assertEquals(3d, Crupier.rebuyTableExposure(3d, -1d, 0d));
        assertEquals(0d, Crupier.rebuyTableExposure(-2d, -1d, -2d));
    }

    @Test
    void losingShowdownAllowsExactTopUpBeforePotContributionsAreCleared() throws Exception {
        CorePlayerController loser = CorePlayerController.local("host");
        CorePlayerController winner = CorePlayerController.remote("guest");
        Crupier dealer = table(NewGameTableDraft.RebuyCapPolicy.BUY_IN, loser, winner);
        loser.setStack(4.30d);
        loser.setBuyin(10d);
        loser.getState().setPotContribution(5.70d);
        winner.setStack(0d);
        winner.setBuyin(10d);
        winner.getState().setPotContribution(10d);

        dealer.rebuyNow("host", 5.70d);
        assertTrue(dealer.getRebuy_now().isEmpty());

        winner.pagar(15.70d, null);
        setField(dealer, "showdownRebuyBalanceReady", true);
        dealer.rebuyNow("host", 5.71d);
        dealer.rebuyNowFromClient("guest", 10d, dealer.immediateRebuyHand(), 1L, 1L, () -> true);

        assertEquals(5.70d, dealer.getRebuy_now().get("host"));
        assertFalse(dealer.getRebuy_now().containsKey("guest"));
        assertEquals(5.70d, loser.getBote());
        double[] row = sealAndProject(dealer, "host");
        loser.nuevaMano();
        assertEquals(10d, row[0]);
        assertEquals(15.70d, row[1]);
        assertEquals(1d, row[2]);
        assertEquals(10d, loser.getStack());
        assertEquals(15.70d, loser.getBuyin());
    }

    @Test
    void hostRejectsRebuyForLocalAndRemoteWinnersAfterClearingThePot() throws Exception {
        CorePlayerController local = CorePlayerController.local("host");
        CorePlayerController remote = CorePlayerController.remote("guest");
        Crupier dealer = table(NewGameTableDraft.RebuyCapPolicy.BUY_IN, local, remote);
        for (CorePlayerController player : List.of(local, remote)) {
            player.setStack(0d);
            player.getState().setPotContribution(10d);
            player.pagar(10.70d, null);
            player.resetBote();
        }

        dealer.rebuyNow("host", 10);
        dealer.rebuyNowFromClient("guest", 10, dealer.immediateRebuyHand(), 1L, 1L, () -> true);

        assertTrue(dealer.getRebuy_now().isEmpty());
    }

    @Test
    void onlyCurrentSeatAndHandCanScheduleAndCancelAPendingRebuy() throws Exception {
        CorePlayerController player = CorePlayerController.remote("guest");
        player.setStack(4.30d);
        player.setBuyin(10d);
        Crupier dealer = table(NewGameTableDraft.RebuyCapPolicy.BUY_IN, player);
        int hand = dealer.immediateRebuyHand();
        dealer.rebuyNowFromClient("guest", 5.70d, hand, 1L, 1L, () -> false);
        assertTrue(dealer.getRebuy_now().isEmpty());

        dealer.rebuyNowFromClient("guest", 5.70d, hand, 1L, 3L, () -> true);
        dealer.rebuyNowFromClient("guest", 0d, hand, 1L, 2L, () -> true);
        dealer.rebuyNowFromClient("guest", 2d, hand, 1L, 4L, () -> true);
        assertEquals(5.70d, dealer.getRebuy_now().get("guest"));

        double[] row = sealAndProject(dealer, "guest");
        dealer.rebuyNowFromClient("guest", 3d, hand, 1L, 5L, () -> true);
        dealer.rebuyNowFromClient("guest", 0d, hand, 1L, 6L, () -> true);
        assertTrue(dealer.getRebuy_now().isEmpty());
        assertEquals(10d, row[0]);
        player.nuevaMano();
        assertEquals(15.70d, player.getBuyin());
        assertEquals(1, dealer.getRebuyCount("guest"));
    }

    @Test
    void hostEnforcesDisabledRebuysAndTheOutstandingRebuyCount() throws Exception {
        CorePlayerController player = CorePlayerController.local("host");
        player.setStack(4d);
        Crupier disabled = table(draft -> draft.setRebuy(false), player);
        disabled.rebuyNow("host", 5d);
        assertTrue(disabled.getRebuy_now().isEmpty());

        Crupier counted = table(draft -> {
            draft.setRebuyLimit(true);
            draft.setRebuyLimitCount(1);
        }, player);
        counted.rebuyNow("host", 5d);
        sealAndProject(counted, "host");
        counted.rebuyNow("host", 1d);
        assertTrue(counted.getRebuy_now().isEmpty());
        player.nuevaMano();
        assertEquals(1, counted.getRebuyCount("host"));
        counted.rebuyNow("host", 1d);
        assertTrue(counted.getRebuy_now().isEmpty());
    }

    @Test
    void settledWinnerDiscardsEarlierRequestBeforeSigningAndApplyingBalances() throws Exception {
        CorePlayerController winner = CorePlayerController.local("host");
        Crupier dealer = table(NewGameTableDraft.RebuyCapPolicy.BUY_IN, winner);
        winner.setBuyin(10);
        winner.setStack(0d);
        winner.pagar(10.70d, null);
        dealer.getRebuy_now().put("host", 10d);

        double[] row = sealAndProject(dealer, "host");
        winner.nuevaMano();

        assertEquals(10.70d, row[0]);
        assertEquals(10d, row[1]);
        assertEquals(0d, row[2]);
        assertEquals(10.70d, winner.getStack());
        assertEquals(10, winner.getBuyin());
    }

    @Test
    void settledHeadroomClampsRequestsAndUsesTheSettledChipLeader() throws Exception {
        CorePlayerController leader = CorePlayerController.local("host");
        CorePlayerController remote = CorePlayerController.remote("guest");
        Crupier dealer = table(NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK, leader, remote);
        leader.setStack(0d);
        leader.pagar(25.70d, null);
        remote.setStack(0d);
        remote.setBuyin(10);
        remote.pagar(7.25d, null);
        dealer.getRebuy_now().put("guest", 25d);

        double[] row = sealAndProject(dealer, "guest");
        remote.nuevaMano();

        assertEquals(25.70d, row[0]);
        assertEquals(28.45d, row[1]);
        assertEquals(1d, row[2]);
        assertEquals(25.70d, remote.getStack());
        assertEquals(28.45d, remote.getBuyin());
    }

    @Test
    void hostStillAcceptsARebuyAfterAnAllInLossIsSettled() throws Exception {
        CorePlayerController local = CorePlayerController.local("host");
        Crupier dealer = table(NewGameTableDraft.RebuyCapPolicy.BUY_IN, local);
        local.setStack(0d);
        local.setBuyin(10);
        local.getState().setPotContribution(10d);

        dealer.rebuyNow("host", 10);
        assertTrue(dealer.getRebuy_now().isEmpty());

        local.resetBote();
        dealer.rebuyNow("host", 10);
        assertEquals(10, dealer.getRebuy_now().get("host"));

        double[] row = sealAndProject(dealer, "host");
        local.nuevaMano();
        assertEquals(10d, row[0]);
        assertEquals(20d, row[1]);
        assertEquals(1d, row[2]);
        assertEquals(10d, local.getStack());
        assertEquals(20, local.getBuyin());
    }

    @Test
    void clientKeepsTheHostRequestUntilItsOwnSettlementCanValidateTheBoundary()
            throws Exception {
        CorePlayerController remote = CorePlayerController.remote("guest");
        Crupier client = table(false, NewGameTableDraft.RebuyCapPolicy.BUY_IN, remote);
        remote.setStack(0d);
        remote.setBuyin(10);
        remote.getState().setPotContribution(10d);

        client.applyRemoteRebuyNow("guest", 10, 1L);
        assertEquals(10, client.getRebuy_now().get("guest"));

        remote.pagar(7.25d, null);
        remote.resetBote();
        double[] row = sealAndProject(client, "guest");
        remote.nuevaMano();
        assertEquals(10d, row[0]);
        assertEquals(12.75d, row[1]);
        assertEquals(1d, row[2]);
        assertEquals(10d, remote.getStack());
        assertEquals(12.75d, remote.getBuyin());
    }

    private static Crupier table(NewGameTableDraft.RebuyCapPolicy policy,
            CorePlayerController... players) throws Exception {
        return table(true, policy, players);
    }

    private static Crupier table(boolean host, NewGameTableDraft.RebuyCapPolicy policy,
            CorePlayerController... players) throws Exception {
        return table(host, draft -> draft.setRebuyCapPolicy(policy), players);
    }

    private static Crupier table(java.util.function.Consumer<NewGameTableDraft> configure,
            CorePlayerController... players) throws Exception {
        return table(true, configure, players);
    }

    private static Crupier table(boolean host,
            java.util.function.Consumer<NewGameTableDraft> configure,
            CorePlayerController... players) throws Exception {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindStructure(null, List.of(new NewGameTableDraft.BlindLevel(0.10d, 0.20d)), 0);
        draft.setBuyin(10);
        configure.accept(draft);
        assertEquals(10, draft.buyin());
        Crupier dealer = new Crupier();
        setField(dealer, "game_session", new GameSession(host ? "host" : "observer", host,
                GameConfigCodecV1.fromSettings(draft.snapshot(), false, "rebuy-test")));
        setField(dealer, "player_controllers", List.of(players));
        setField(dealer, "peer_controllers", Map.of());
        for (CorePlayerController player : players) {
            dealer.getNick2player().put(player.getNickname(), player);
            player.bindCommittedRebuy(() -> dealer.consumeCommittedRebuy(player.getNickname()));
        }
        return dealer;
    }

    private static void setField(Crupier dealer, String name, Object value) throws Exception {
        var field = Crupier.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(dealer, value);
    }

    private static double[] sealAndProject(Crupier dealer, String nickname) throws Exception {
        var seal = Crupier.class.getDeclaredMethod("commitPendingRebuysForBoundary");
        seal.setAccessible(true);
        seal.invoke(dealer);
        var project = Crupier.class.getDeclaredMethod("collectNextHandBalanceRows");
        project.setAccessible(true);
        Map<?, ?> rows = (Map<?, ?>) project.invoke(dealer);
        return (double[]) rows.get(nickname);
    }

    @Test
    void globalBotRebuyIsTheMasterForStandardAndCustomBots() {
        assertFalse(Crupier.botAutomaticRebuyAllowed(true, false, true,
                true));
        assertFalse(Crupier.botAutomaticRebuyAllowed(true, false, false,
                true));
        assertTrue(Crupier.botAutomaticRebuyAllowed(true, true, true,
                false));
        assertTrue(Crupier.botAutomaticRebuyAllowed(true, true, false,
                true));
        assertFalse(Crupier.botAutomaticRebuyAllowed(true, true, false,
                false));
    }

    @Test
    void humanRebuyDoesNotDependOnBotSettings() {
        assertTrue(Crupier.botAutomaticRebuyAllowed(false, false, false,
                false));
    }

    @Test
    void hostCanScheduleAnyEstablishedBotButNotWarmingOrExitedOnes() {
        assertTrue(Crupier.botManualRebuyAllowed(true, true, false, false));
        assertFalse(Crupier.botManualRebuyAllowed(false, true, false, false));
        assertFalse(Crupier.botManualRebuyAllowed(true, false, false, false));
        assertFalse(Crupier.botManualRebuyAllowed(true, true, true, false));
        assertFalse(Crupier.botManualRebuyAllowed(true, true, false, true));
    }
}
