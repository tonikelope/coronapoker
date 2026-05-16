/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.GameFrame;
import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.Player;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * N-handed no-limit Texas hold'em simulator. Generalizes the heads-up
 * harness so the bot's full-ring decision paths get exercised: UTG cold
 * fold, multi-way limped pots, isolation raises, multi-opponent c-bets,
 * and the cliffs in equity that only appear with 3+ live opponents.
 *
 * <p>This is the target environment for AAA validation — CoronaPoker is
 * played multi-way (3-9 seats), not heads-up. The button rotates every
 * hand and per-street action order follows standard rules:</p>
 *
 * <ul>
 *   <li>Preflop first-to-act = UTG (button + 3 mod N).</li>
 *   <li>Postflop first-to-act = SB (button + 1 mod N), or the first
 *       active seat after that.</li>
 *   <li>A betting round closes when no active live-stack player still
 *       owes an action and every active player has matched the current
 *       bet (or is all-in).</li>
 * </ul>
 *
 * <p>Mirrors {@link HeadsUpSimulator} for blind posting, action
 * recording, c-bet detection, OpponentTracker feeding, and showdown
 * resolution. Showdown selects winners by pairwise comparison; the pot
 * is split evenly if multiple seats tie.</p>
 */
public final class MultiwaySimulator {

    static {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        GameFrame.CIEGA_PEQUEÑA = 1.0f;
    }

    /** Safety net against infinite action loops within a street. */
    private static final int MAX_ACTIONS_PER_STREET = 120;

    private final int numSeats;
    private final TestBotPlayer[] players;
    private final Bot[] bots;
    private final BotStats[] stats;
    private final TestDealer dealer;
    private final BotEvaluator evaluator;
    private final Random deckRng;
    private final float bigBlind;
    private final float smallBlind;
    private final float startingStack;

    // Per-hand transient flags (parallel arrays indexed by seat).
    private final boolean[] handVoluntary;
    private final boolean[] handRaisedPreflop;
    private final boolean[] handCbetOpp;
    private final boolean[] handCbetDone;

    private int buttonIdx;
    private int handCounter = 0;

    public MultiwaySimulator(int numSeats, long seed, float startingStack,
                             float bigBlind, BotEvaluator evaluator) {
        if (numSeats < 3 || numSeats > 9) {
            throw new IllegalArgumentException("numSeats must be 3..9, got " + numSeats);
        }
        this.numSeats = numSeats;
        this.deckRng = new Random(seed);
        this.bigBlind = bigBlind;
        this.smallBlind = bigBlind / 2.0f;
        this.startingStack = startingStack;
        this.evaluator = evaluator;
        this.players = new TestBotPlayer[numSeats];
        this.bots = new Bot[numSeats];
        this.stats = new BotStats[numSeats];
        this.handVoluntary = new boolean[numSeats];
        this.handRaisedPreflop = new boolean[numSeats];
        this.handCbetOpp = new boolean[numSeats];
        this.handCbetDone = new boolean[numSeats];
        List<TestBotPlayer> seating = new ArrayList<>(numSeats);
        for (int i = 0; i < numSeats; i++) {
            players[i] = new TestBotPlayer("BOT_" + i, startingStack);
            stats[i] = new BotStats("BOT_" + i);
            seating.add(players[i]);
        }
        this.dealer = new TestDealer();
        this.dealer.setBlinds(smallBlind, bigBlind);
        this.dealer.setSeating(seating);
        for (int i = 0; i < numSeats; i++) {
            bots[i] = new Bot(players[i]);
            bots[i].setContext(dealer, evaluator);
            bots[i].setRng(new Random(seed ^ (0x10001L * (i + 1))));
        }
        this.buttonIdx = 0;
    }

    public int numSeats() {
        return numSeats;
    }

    public TestBotPlayer player(int seat) {
        return players[seat];
    }

    public Bot bot(int seat) {
        return bots[seat];
    }

    public BotStats stats(int seat) {
        return stats[seat];
    }

    public TestDealer dealerView() {
        return dealer;
    }

    /**
     * Replace the bot at a seat with an externally constructed instance.
     * Required for benchmarking the production bot against fixed-strategy
     * opponents and for assigning per-seat difficulties in mixed tables.
     */
    public void setBot(int seat, Bot bot) {
        bots[seat] = bot;
        bots[seat].setContext(dealer, evaluator);
    }

    /** Bulk difficulty assignment; array length must equal numSeats. */
    public void setSeatDifficulties(Bot.Difficulty... difficulties) {
        if (difficulties.length != numSeats) {
            throw new IllegalArgumentException("difficulties.length must equal numSeats");
        }
        for (int i = 0; i < numSeats; i++) {
            bots[i].setDifficulty(difficulties[i]);
        }
    }

    public void resetStacks() {
        for (TestBotPlayer p : players) {
            p.setStack(startingStack);
        }
    }

    public int currentButton() {
        return buttonIdx;
    }

    /**
     * Play one hand to completion. Rotates the button afterwards.
     * Returns the set of winners (one or more, in case of split pot) and
     * the final pot size.
     */
    public HandResult playOneHand() {
        int[] deck = shuffledDeck();
        dealer.resetBoard();

        for (int i = 0; i < numSeats; i++) {
            players[i].setHoleCards(deck[i], deck[i + numSeats]);
            players[i].setActivo(true);
            players[i].setBet(0f);
        }
        int[] runout = {
                deck[2 * numSeats], deck[2 * numSeats + 1], deck[2 * numSeats + 2],
                deck[2 * numSeats + 3], deck[2 * numSeats + 4]
        };

        Arrays.fill(handVoluntary, false);
        Arrays.fill(handRaisedPreflop, false);
        Arrays.fill(handCbetOpp, false);
        Arrays.fill(handCbetDone, false);

        float[] startStacks = new float[numSeats];
        for (int i = 0; i < numSeats; i++) {
            startStacks[i] = players[i].getStack();
        }

        int sbIdx = (buttonIdx + 1) % numSeats;
        int bbIdx = (buttonIdx + 2) % numSeats;
        int utgIdx = (buttonIdx + 3) % numSeats;
        dealer.setRoles(players[buttonIdx].getNickname(),
                players[sbIdx].getNickname(),
                players[bbIdx].getNickname(),
                players[utgIdx].getNickname());
        dealer.setStreet(Crupier.PREFLOP);

        chargeBet(players[sbIdx], smallBlind);
        chargeBet(players[bbIdx], bigBlind);
        dealer.setPot(smallBlind + bigBlind);
        dealer.setCurrentBet(bigBlind);
        dealer.setLastRaise(bigBlind);
        dealer.setBetCount(0);
        dealer.setLimpersCount(0);
        dealer.setLastAggressor(null);

        for (Bot b : bots) {
            b.resetBot();
        }
        handCounter++;
        for (int i = 0; i < numSeats; i++) {
            trackerFor(players[i]).recordHandPlayed();
        }

        boolean handReachedShowdown = false;
        Set<Integer> winners = null;

        for (int street = Crupier.PREFLOP; street <= Crupier.RIVER; street++) {
            dealer.setStreet(street);
            if (street > Crupier.PREFLOP) {
                openStreet(runout, street);
                if (street == Crupier.FLOP) {
                    for (int i = 0; i < numSeats; i++) {
                        handCbetOpp[i] = handRaisedPreflop[i] && players[i].isActivo();
                    }
                }
            }

            int firstToAct = (street == Crupier.PREFLOP) ? utgIdx : sbIdx;
            BettingResult result = runBettingRound(firstToAct, street == Crupier.PREFLOP);

            if (result == BettingResult.HAND_OVER_FOLD) {
                int aliveIdx = findOnlyActive();
                float pot = dealer.getBote_total();
                players[aliveIdx].setStack(players[aliveIdx].getStack() + pot);
                winners = new HashSet<>();
                winners.add(aliveIdx);
                finalizeHandStats(startStacks, false, winners);
                rotateButton();
                return new HandResult(winners, pot);
            }
            if (countActive() <= 1) {
                break;
            }
            if (allActiveAllIn()) {
                while (dealer.getBoardSize() < 5) {
                    int nextIdx = Math.min(dealer.getBoardSize(), 4);
                    dealer.appendBoardCard(runout[nextIdx]);
                }
                break;
            }
        }

        winners = resolveShowdown();
        handReachedShowdown = true;
        float pot = dealer.getBote_total();
        float share = pot / winners.size();
        for (int idx : winners) {
            players[idx].setStack(players[idx].getStack() + share);
        }
        finalizeHandStats(startStacks, handReachedShowdown, winners);
        rotateButton();
        return new HandResult(winners, pot);
    }

    private void rotateButton() {
        buttonIdx = (buttonIdx + 1) % numSeats;
    }

    private int findOnlyActive() {
        for (int i = 0; i < numSeats; i++) {
            if (players[i].isActivo()) {
                return i;
            }
        }
        throw new IllegalStateException("No active players left");
    }

    private int countActive() {
        int n = 0;
        for (TestBotPlayer p : players) {
            if (p.isActivo()) {
                n++;
            }
        }
        return n;
    }

    private boolean allActiveAllIn() {
        for (int i = 0; i < numSeats; i++) {
            if (players[i].isActivo() && players[i].getStack() > 0f) {
                return false;
            }
        }
        return true;
    }

    private void openStreet(int[] runout, int street) {
        if (street == Crupier.FLOP) {
            dealer.appendBoardCard(runout[0]);
            dealer.appendBoardCard(runout[1]);
            dealer.appendBoardCard(runout[2]);
        } else if (street == Crupier.TURN) {
            dealer.appendBoardCard(runout[3]);
        } else if (street == Crupier.RIVER) {
            dealer.appendBoardCard(runout[4]);
        }
        dealer.setCurrentBet(0f);
        dealer.setLastRaise(0f);
        dealer.setBetCount(0);
        dealer.setLastAggressor(null);
        for (TestBotPlayer p : players) {
            p.setBet(0f);
        }
    }

    private BettingResult runBettingRound(int firstToAct, boolean preflop) {
        Set<Integer> needToAct = new HashSet<>();
        for (int i = 0; i < numSeats; i++) {
            if (players[i].isActivo()) {
                needToAct.add(i);
            }
        }
        int toAct = firstToAct;
        // iterations counts every loop pass so the safety net catches
        // pathological scenarios (e.g. all remaining players all-in
        // without exiting via allActiveAllIn check above) regardless
        // of whether the pass corresponded to a real decision.
        int iterations = 0;

        while (iterations < MAX_ACTIONS_PER_STREET) {
            iterations++;
            if (countActive() <= 1) {
                return BettingResult.HAND_OVER_FOLD;
            }
            if (needToAct.isEmpty() && allActiveMatched()) {
                return BettingResult.STREET_DONE;
            }
            if (allActiveAllIn()) {
                // No one left who can act: ship the street.
                return BettingResult.STREET_DONE;
            }
            if (!players[toAct].isActivo()) {
                toAct = (toAct + 1) % numSeats;
                continue;
            }
            if (players[toAct].getStack() <= 0f) {
                needToAct.remove(toAct);
                toAct = (toAct + 1) % numSeats;
                continue;
            }
            if (!needToAct.contains(toAct) && allActiveMatched()) {
                toAct = (toAct + 1) % numSeats;
                continue;
            }

            int decision = bots[toAct].calculateBotDecision(countActive() - 1);
            float currentBet = dealer.getApuesta_actual();
            float toCall = currentBet - players[toAct].getBet();
            int street = dealer.getStreet();

            if (decision == Player.FOLD) {
                if (toCall <= 0f) {
                    // "FOLD" with nothing to call → treat as check (no state change).
                    needToAct.remove(toAct);
                    if (!preflop) {
                        recordPostflopCheck(toAct);
                    }
                } else {
                    players[toAct].setActivo(false);
                    needToAct.remove(toAct);
                    if (!preflop) {
                        recordPostflopFold(toAct);
                    }
                }
            } else if (decision == Player.CHECK) {
                if (toCall > 0f) {
                    float pay = Math.min(toCall, players[toAct].getStack());
                    chargeBet(players[toAct], pay);
                    dealer.setPot(dealer.getBote_total() + pay);
                    if (preflop) {
                        recordPreflopVoluntary(toAct);
                    } else {
                        recordPostflopCall(toAct);
                    }
                } else if (!preflop) {
                    recordPostflopCheck(toAct);
                }
                needToAct.remove(toAct);
            } else if (decision == Player.BET) {
                float newBet = bots[toAct].getBetSize();
                float maxAffordable = players[toAct].getBet() + players[toAct].getStack();
                if (newBet > maxAffordable) {
                    newBet = maxAffordable;
                }
                if (newBet <= currentBet) {
                    if (toCall > 0f) {
                        float pay = Math.min(toCall, players[toAct].getStack());
                        chargeBet(players[toAct], pay);
                        dealer.setPot(dealer.getBote_total() + pay);
                        if (preflop) {
                            recordPreflopVoluntary(toAct);
                        } else {
                            recordPostflopCall(toAct);
                        }
                    } else if (!preflop) {
                        recordPostflopCheck(toAct);
                    }
                    needToAct.remove(toAct);
                } else {
                    float raiseSize = newBet - currentBet;
                    float pay = newBet - players[toAct].getBet();
                    pay = Math.min(pay, players[toAct].getStack());
                    chargeBet(players[toAct], pay);
                    dealer.setPot(dealer.getBote_total() + pay);
                    dealer.setCurrentBet(players[toAct].getBet());
                    dealer.setLastRaise(raiseSize);
                    dealer.setBetCount(dealer.getConta_bet() + 1);
                    dealer.setLastAggressor(players[toAct]);
                    if (preflop) {
                        recordPreflopRaise(toAct);
                    } else {
                        recordPostflopBetRaise(toAct, street, toCall);
                    }
                    needToAct.clear();
                    for (int i = 0; i < numSeats; i++) {
                        if (i != toAct && players[i].isActivo() && players[i].getStack() > 0f) {
                            needToAct.add(i);
                        }
                    }
                }
            }
            toAct = (toAct + 1) % numSeats;
        }
        return BettingResult.STREET_DONE;
    }

    private boolean allActiveMatched() {
        float c = dealer.getApuesta_actual();
        for (int i = 0; i < numSeats; i++) {
            if (players[i].isActivo() && players[i].getStack() > 0f
                    && Math.abs(players[i].getBet() - c) > 0.0001f) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> resolveShowdown() {
        Set<Integer> winners = new HashSet<>();
        int[] activeIndices = new int[numSeats];
        int activeCount = 0;
        for (int i = 0; i < numSeats; i++) {
            if (players[i].isActivo()) {
                activeIndices[activeCount++] = i;
            }
        }
        if (activeCount == 0) {
            throw new IllegalStateException("Showdown with no active players");
        }
        int boardSize = dealer.getBoardSize();
        winners.add(activeIndices[0]);
        for (int k = 1; k < activeCount; k++) {
            int candidate = activeIndices[k];
            int representative = winners.iterator().next();
            int cmp = evaluator.compareHands(
                    buildHand(representative, boardSize),
                    buildHand(candidate, boardSize));
            if (cmp == 2) {
                winners.clear();
                winners.add(candidate);
            } else if (cmp == 0) {
                winners.add(candidate);
            }
        }
        return winners;
    }

    private int[] buildHand(int seat, int boardSize) {
        int[] hand = new int[2 + boardSize];
        hand[0] = players[seat].getHoleCard1Index();
        hand[1] = players[seat].getHoleCard2Index();
        for (int i = 0; i < boardSize; i++) {
            hand[2 + i] = dealer.getBoardCardIndex(i);
        }
        return hand;
    }

    private void finalizeHandStats(float[] startStacks, boolean reachedShowdown, Set<Integer> winners) {
        for (int i = 0; i < numSeats; i++) {
            stats[i].handsPlayed++;
            stats[i].netChipsWon += (players[i].getStack() - startStacks[i]);
            if (handVoluntary[i]) {
                stats[i].handsVoluntaryMoneyPreflop++;
            }
            if (handRaisedPreflop[i]) {
                stats[i].handsWithPreflopRaise++;
            }
            if (handCbetOpp[i]) {
                stats[i].cbetOpportunities++;
                if (handCbetDone[i]) {
                    stats[i].cbetExecuted++;
                }
            }
            if (reachedShowdown && players[i].isActivo()) {
                stats[i].handsReachedShowdown++;
                if (winners.contains(i)) {
                    stats[i].handsWonAtShowdown++;
                }
            }
            if (winners.contains(i)) {
                stats[i].handsWon++;
            }
        }
    }

    private Bot.OpponentTracker trackerFor(TestBotPlayer p) {
        return Bot.TRACKER_MEMORY.computeIfAbsent(p.getNickname(),
                k -> new Bot.OpponentTracker());
    }

    private void recordPreflopVoluntary(int seat) {
        handVoluntary[seat] = true;
        trackerFor(players[seat]).recordVPIP(handCounter);
    }

    private void recordPreflopRaise(int seat) {
        handVoluntary[seat] = true;
        handRaisedPreflop[seat] = true;
        for (int i = 0; i < numSeats; i++) {
            if (i != seat) {
                handRaisedPreflop[i] = false;
            }
        }
        Bot.OpponentTracker t = trackerFor(players[seat]);
        t.recordVPIP(handCounter);
        t.recordPFR(handCounter);
    }

    private void recordPostflopBetRaise(int seat, int street, float toCall) {
        stats[seat].postflopBetsRaises++;
        if (street == Crupier.FLOP && toCall == 0f
                && handCbetOpp[seat] && !handCbetDone[seat]) {
            handCbetDone[seat] = true;
        }
        trackerFor(players[seat]).recordPostFlopBetOrRaise();
    }

    private void recordPostflopCall(int seat) {
        stats[seat].postflopCalls++;
        trackerFor(players[seat]).recordPostFlopCall();
    }

    private void recordPostflopCheck(int seat) {
        stats[seat].postflopChecks++;
    }

    private void recordPostflopFold(int seat) {
        stats[seat].postflopFolds++;
    }

    private void chargeBet(TestBotPlayer p, float amount) {
        p.setStack(p.getStack() - amount);
        p.setBet(p.getBet() + amount);
    }

    private int[] shuffledDeck() {
        int[] deck = new int[52];
        for (int i = 0; i < 52; i++) {
            deck[i] = i;
        }
        for (int i = 51; i > 0; i--) {
            int j = deckRng.nextInt(i + 1);
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        return deck;
    }

    public static final class HandResult {
        public final Set<Integer> winners;
        public final float pot;

        public HandResult(Set<Integer> winners, float pot) {
            this.winners = winners;
            this.pot = pot;
        }
    }

    private enum BettingResult {
        STREET_DONE,
        HAND_OVER_FOLD
    }
}
