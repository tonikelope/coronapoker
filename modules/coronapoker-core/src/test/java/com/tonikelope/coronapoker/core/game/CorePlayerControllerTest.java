package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.bot.context.BotPlayerView;
import com.tonikelope.coronapoker.bot.context.DealerView;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableSnapshotMapper;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CorePlayerControllerTest {

    @Test
    void foldedShowAllowanceStartsAtFiveAndNeverBecomesNegative() {
        CorePlayerController player = CorePlayerController.local("player");

        assertEquals(5, player.getParguela_counter());
        for (int i = 0; i < 7; i++) {
            player.consumeParguelaShow();
        }
        assertEquals(0, player.getParguela_counter());
    }

    @Test
    void replayableBotUsesInjectedEntropyForItsInitialPersonality() {
        CountingRandom replay = new CountingRandom(12345L);

        CorePlayerController.bot("CoronaBot$1", replay);

        assertEquals(2, replay.boundedCalls.get(),
                "skill and style must be drawn from scenario entropy during "
                + "construction, never from the process CSPRNG");
    }

    @Test
    void everyContributionRegistersThePlayerInTheCurrentPot() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        AtomicInteger registrations = new AtomicInteger();
        player.bindPotRegistration(registrations::incrementAndGet);

        player.setBet(0.20d);
        player.postAnte(0.05d);

        assertEquals(2, registrations.get());
        assertEquals(0.25d, player.getBote());
        assertEquals(9.75d, player.getStack());
    }

    private static final class CountingRandom extends Random {
        private final AtomicInteger boundedCalls = new AtomicInteger();

        private CountingRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            boundedCalls.incrementAndGet();
            return super.nextInt(bound);
        }
    }

    @Test
    void anteIsDeadMoneyAndDoesNotChangeTheStreetBet() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);

        player.setBet(0.20d);
        double posted = player.postAnte(0.10d);

        assertEquals(0.10d, posted);
        assertEquals(0.20d, player.getBet());
        assertEquals(0.30d, player.getBote());
        assertEquals(9.70d, player.getStack());
    }

    @Test
    void shortStackAntePostsOnlyTheRemainingStackAndMarksAllIn() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(0.05d);

        double posted = player.postAnte(0.10d);

        assertEquals(0.05d, posted);
        assertEquals(0d, player.getBet());
        assertEquals(0.05d, player.getBote());
        assertEquals(0d, player.getStack());
        assertEquals(GamePlayerController.ALLIN, player.getDecision());
    }

    @Test
    void postedStraddleBecomesTheCanonicalPositionForEveryFrontend() {
        CorePlayerController player = CorePlayerController.local("utg");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0d));
        player.applyCurrentHandPosition();

        player.postStraddle(0.40d);

        assertEquals(PlayerState.Position.STRADDLE,
                player.getState().snapshot().position());
    }

    @Test
    void dealerStraddleKeepsTheCombinedCanonicalPosition() {
        CorePlayerController player = CorePlayerController.remote("dealer");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0d));
        player.applyCurrentHandPosition();

        player.postStraddle(0.40d);

        assertEquals(PlayerState.Position.DEALER_STRADDLE,
                player.getState().snapshot().position());
    }

    @Test
    void underTheGunIsAnIndependentFrontendBadge() {
        CorePlayerController player = CorePlayerController.remote("dealer");
        player.bindDealer(new StubDealer(0d));
        player.applyCurrentHandPosition();

        player.setUTG();

        PlayerState.Snapshot state = player.getState().snapshot();
        TableSnapshot.PlayerSnapshot snapshot = TableSnapshotMapper.player(state);
        assertEquals(PlayerState.Position.DEALER, state.position());
        assertTrue(state.underTheGun());
        assertEquals(TableSnapshot.Position.DEALER, snapshot.position());
        assertTrue(snapshot.underTheGun());

        player.disableUTG();

        assertEquals(PlayerState.Position.DEALER,
                player.getState().snapshot().position());
        assertFalse(player.getState().snapshot().underTheGun());
    }

    @Test
    void openingNextHandConsumesCommittedRebuyBeforePendingPayout() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(0d);
        player.setBuyin(10);
        player.setPagar(2d);
        AtomicInteger consumptions = new AtomicInteger();
        player.bindCommittedRebuy(() -> {
            consumptions.incrementAndGet();
            return 10;
        });

        player.nuevaMano();

        assertEquals(1, consumptions.get());
        assertEquals(12d, player.getStack());
        assertEquals(20, player.getBuyin());
        assertEquals(0d, player.getPagar());
    }

    @Test
    void dealerManagedTimeoutChecksWhenNothingMustBeCalled() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0d));
        AtomicInteger completions = new AtomicInteger();
        player.bindTurnCompletionSignal(completions::incrementAndGet);
        player.esTuTurno();

        assertTrue(player.requiresDealerManagedTurnTimeout());
        assertTrue(player.submitTurnTimeoutDecision());
        assertEquals(GamePlayerController.CHECK, player.getDecision());
        assertTrue(player.isTimeout());
        assertFalse(player.isTurno());
        assertEquals(1, completions.get());
    }

    @Test
    void dealerManagedTimeoutFoldsInsteadOfCallingForMoney() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0.20d));
        AtomicInteger completions = new AtomicInteger();
        player.bindTurnCompletionSignal(completions::incrementAndGet);
        player.esTuTurno();

        assertTrue(player.submitTurnTimeoutDecision());
        assertEquals(GamePlayerController.FOLD, player.getDecision());
        assertEquals(10d, player.getStack());
        assertEquals(0d, player.getBet());
        assertTrue(player.isTimeout());
        assertEquals(1, completions.get());
    }

    @Test
    void everyAcceptedLocalFoldEmitsItsPresentationSignalExactlyOnce() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0.20d));
        AtomicInteger folds = new AtomicInteger();
        player.bindAcceptedLocalFoldSignal(folds::incrementAndGet);
        player.esTuTurno();

        assertTrue(player.submitDecision(GamePlayerController.FOLD, 0d));
        assertEquals(1, folds.get());
        assertFalse(player.submitDecision(GamePlayerController.FOLD, 0d));
        assertEquals(1, folds.get(),
                "a stale duplicate input must not replay the funny fold cue");
    }

    @Test
    void nonFoldActionsNeverEmitTheLocalFoldSignal() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0d));
        AtomicInteger folds = new AtomicInteger();
        player.bindAcceptedLocalFoldSignal(folds::incrementAndGet);
        player.esTuTurno();

        assertTrue(player.submitDecision(GamePlayerController.CHECK, 0d));
        assertEquals(0, folds.get());
    }

    @Test
    void staleTimeoutCannotOverrideACompletedManualDecision() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0d));
        AtomicInteger completions = new AtomicInteger();
        player.bindTurnCompletionSignal(completions::incrementAndGet);
        player.esTuTurno();

        assertTrue(player.submitDecision(GamePlayerController.CHECK, 0d));
        assertFalse(player.submitTurnTimeoutDecision());
        assertEquals(GamePlayerController.CHECK, player.getDecision());
        assertFalse(player.isTimeout());
        assertEquals(1, completions.get());
    }

    @Test
    void recoveredDecisionCompletesTheCanonicalTurnWithoutFrontendInput() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.bindDealer(new StubDealer(0d));
        AtomicInteger completions = new AtomicInteger();
        player.bindTurnCompletionSignal(completions::incrementAndGet);
        player.esTuTurno();

        assertTrue(player.submitRecoveredDecision(GamePlayerController.CHECK, 0d));
        assertEquals(GamePlayerController.CHECK, player.getDecision());
        assertFalse(player.isTurno());
        assertEquals(1, completions.get());
    }

    @Test
    void spectatorTransitionClearsCompletedHandAllInState() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        player.postAnte(10d);

        assertEquals(GamePlayerController.ALLIN, player.getDecision());
        assertEquals(10d, player.getBote());

        player.setSpectator("spectator");

        assertTrue(player.isSpectator());
        assertFalse(player.isActivo());
        assertEquals(GamePlayerController.FOLD, player.getDecision());
        assertEquals(0d, player.getBote());
        assertEquals("spectator", player.getLastActionString());
    }

    @Test
    void brokeBotExitPreservesTheCanonicalSpectatorRole() {
        CorePlayerController player = CorePlayerController.bot("CoronaBot$1");
        player.setStack(0d);

        player.setExit();

        assertTrue(player.isExit());
        assertTrue(player.isSpectator());
        assertFalse(player.isActivo());
        assertEquals(GamePlayerController.FOLD, player.getDecision());
        assertEquals(0d, player.getBote());
    }

    @Test
    void nativeShowdownOutcomeOffersAnActuallyMuckedHumanLoserForIwtsth() {
        CorePlayerController remote = CorePlayerController.remote("rival");
        remote.getHoleCard1().setVisibleCard(true);
        remote.getHoleCard2().setVisibleCard(true);
        remote.getHoleCard1().iniciarConValorNumerico(1);
        remote.getHoleCard2().iniciarConValorNumerico(2);

        remote.applyShowdownResult(true, "winner");
        assertFalse(remote.isIwtsthCandidate());

        remote.applyShowdownResult(false, "loser");
        assertTrue(remote.isLoser());
        assertTrue(remote.isIwtsthCandidate());

        remote.getHoleCard1().destapar(false);
        assertFalse(remote.isIwtsthCandidate());
    }

    @Test
    void nativeShowdownOutcomeAlsoOffersAMuckedBotLikeSwing() {
        CorePlayerController bot = CorePlayerController.bot("CoronaBot$1");
        bot.getHoleCard1().setVisibleCard(true);
        bot.getHoleCard2().setVisibleCard(true);
        bot.getHoleCard1().iniciarConValorNumerico(1);
        bot.getHoleCard2().iniciarConValorNumerico(2);

        bot.applyShowdownResult(false, "loser");

        assertTrue(bot.isLoser());
        assertTrue(bot.isIwtsthCandidate());
    }

    private static final class StubDealer implements DealerView {
        private final double currentBet;

        StubDealer(double currentBet) {
            this.currentBet = currentBet;
        }

        @Override public int getStreet() { return 0; }
        @Override public double getBote_total() { return 0d; }
        @Override public double getApuesta_actual() { return currentBet; }
        @Override public double getUltimo_raise() { return 0.20d; }
        @Override public double getCiega_grande() { return 0.20d; }
        @Override public double getCiega_pequeña() { return 0.10d; }
        @Override public int getConta_bet() { return 0; }
        @Override public int getLimpersCount() { return 0; }
        @Override public String getDealer_nick() { return "dealer"; }
        @Override public String getSb_nick() { return "small"; }
        @Override public String getBb_nick() { return "big"; }
        @Override public String getUtg_nick() { return "utg"; }
        @Override public String getStraddleUtgNick() { return null; }
        @Override public BotPlayerView getLast_aggressor() { return null; }
        @Override public int getJugadoresActivos() { return 2; }
        @Override public List<? extends BotPlayerView> getPlayersInSeatingOrder() {
            return List.of();
        }
        @Override public int getBoardCardIndex(int index) { return -1; }
        @Override public int getBoardSize() { return 0; }
    }
}
