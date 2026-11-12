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
import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * N-handed no-limit Texas hold'em simulator. Generalizes the heads-up harness
 * so the bot's full-ring decision paths get exercised: UTG cold fold, multi-way
 * limped pots, isolation raises, multi-opponent c-bets, and the cliffs in
 * equity that only appear with 3+ live opponents.
 *
 * <p>
 * This is the target environment for AAA validation — CoronaPoker is played
 * multi-way (3-10 seats), not heads-up. The button rotates every hand and
 * per-street action order follows standard rules:</p>
 *
 * <ul>
 * <li>Preflop first-to-act = UTG (button + 3 mod N).</li>
 * <li>Postflop first-to-act = SB (button + 1 mod N), or the first active seat
 * after that.</li>
 * <li>A betting round closes when no active live-stack player still owes an
 * action and every active player has matched the current bet (or is
 * all-in).</li>
 * </ul>
 *
 * <p>
 * Mirrors {@link HeadsUpSimulator} for blind posting, action recording, c-bet
 * detection, OpponentTracker feeding and settlement with the table's side pots.</p>
 */
public final class MultiwaySimulator {

    static {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /**
     * Safety net against infinite action loops within a street.
     */
    private static final int MAX_ACTIONS_PER_STREET = 120;

    private final int numSeats;
    private final TestBotPlayer[] players;
    private final Bot[] bots;
    private final BotStats[] stats;
    private final TestDealer dealer;
    private final BotEvaluator evaluator;
    private final Random deckRng;
    private final double bigBlind;
    private final double smallBlind;
    private final double startingStack;

    // Per-hand transient flags (parallel arrays indexed by seat).
    private final boolean[] handVoluntary;
    private final boolean[] handRaisedPreflop;

    private int buttonIdx;
    private int handCounter = 0;

    public MultiwaySimulator(int numSeats, long seed, double startingStack,
            double bigBlind, BotEvaluator evaluator) {
        if (numSeats < 3 || numSeats > 10) {
            throw new IllegalArgumentException("numSeats must be 3..10, got " + numSeats);
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
            bots[i] = new Bot(players[i], new Random(seed ^ (0x10001L * (i + 1))));
            bots[i].setContext(dealer, evaluator);
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

    /**
     * Bulk difficulty assignment; array length must equal numSeats.
     */
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
     * Play one hand to completion. Rotates the button afterwards. Returns the
     * set of winners (one or more, in case of split pot) and the final pot
     * size.
     */
    public HandResult playOneHand() {
        int[] deck = shuffledDeck();
        dealer.resetBoard();

        for (int i = 0; i < numSeats; i++) {
            players[i].setHoleCards(deck[i], deck[i + numSeats]);
            players[i].setActivo(true);
            players[i].resetHandContribution();
        }
        int[] runout = {
            deck[2 * numSeats], deck[2 * numSeats + 1], deck[2 * numSeats + 2],
            deck[2 * numSeats + 3], deck[2 * numSeats + 4]
        };

        Arrays.fill(handVoluntary, false);
        Arrays.fill(handRaisedPreflop, false);

        double[] startStacks = new double[numSeats];
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

        players[sbIdx].commit(Math.min(smallBlind, players[sbIdx].getStack()));
        players[bbIdx].commit(Math.min(bigBlind, players[bbIdx].getStack()));
        dealer.setPot(players[sbIdx].getBet() + players[bbIdx].getBet());
        dealer.setCurrentBet(Math.max(players[sbIdx].getBet(), players[bbIdx].getBet()));
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
            }

            int firstToAct = (street == Crupier.PREFLOP) ? utgIdx : sbIdx;
            boolean foldedOut = SimulatedBettingRound.play(dealer, players, bots, firstToAct,
                    MAX_ACTIONS_PER_STREET, this::recordAction);

            if (foldedOut) {
                int aliveIdx = findOnlyActive();
                double pot = dealer.getBote_total();
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
            if (noMoreBetting()) {
                while (dealer.getBoardSize() < 5) {
                    int nextIdx = Math.min(dealer.getBoardSize(), 4);
                    dealer.appendBoardCard(runout[nextIdx]);
                }
                break;
            }
        }

        winners = SimulatedShowdown.settle(players, dealer, evaluator);
        handReachedShowdown = true;
        double pot = dealer.getBote_total();
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

    private boolean noMoreBetting() {
        int withChips = 0;
        for (TestBotPlayer player : players) {
            if (player.isActivo() && player.getStack() > 0 && ++withChips > 1) {
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

    private void recordAction(int seat, boolean folded, boolean raised, long paid, long toCall) {
        int street = dealer.getStreet();
        if (SimulatedBettingRound.continuationBetOpportunity(dealer, players[seat], toCall, raised)) {
            stats[seat].cbetOpportunities++;
            if (raised) {
                stats[seat].cbetExecuted++;
            }
        }
        if (street == Crupier.PREFLOP) {
            if (raised) {
                recordPreflopRaise(seat);
            } else if (paid > 0) {
                recordPreflopVoluntary(seat);
            }
        } else if (folded) {
            recordPostflopFold(seat);
        } else if (raised) {
            recordPostflopBetRaise(seat, street);
        } else if (paid > 0) {
            recordPostflopCall(seat);
        } else {
            recordPostflopCheck(seat);
        }
    }

    private void finalizeHandStats(double[] startStacks, boolean reachedShowdown, Set<Integer> winners) {
        for (int i = 0; i < numSeats; i++) {
            stats[i].handsPlayed++;
            stats[i].netChipsWon += (players[i].getStack() - startStacks[i]);
            if (handVoluntary[i]) {
                stats[i].handsVoluntaryMoneyPreflop++;
            }
            if (handRaisedPreflop[i]) {
                stats[i].handsWithPreflopRaise++;
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
        return dealer.trackerFor(p.getNickname());
    }

    private void recordPreflopVoluntary(int seat) {
        handVoluntary[seat] = true;
        trackerFor(players[seat]).recordVPIP(handCounter);
    }

    private void recordPreflopRaise(int seat) {
        handVoluntary[seat] = true;
        handRaisedPreflop[seat] = true;
        Bot.OpponentTracker t = trackerFor(players[seat]);
        t.recordVPIP(handCounter);
        t.recordPFR(handCounter);
    }

    private void recordPostflopBetRaise(int seat, int street) {
        stats[seat].postflopBetsRaises++;
        // Classify the aggressive action by made-hand strength (value vs bluff)
        // to measure readability/exploitability of the bot in the multi-way
        // environment it is actually played in.
        double strength = handStrengthOf(seat);
        if (strength >= BotStats.VALUE_BET_STRENGTH) {
            stats[seat].postflopValueBets++;
        } else if (strength <= BotStats.BLUFF_BET_STRENGTH) {
            stats[seat].postflopBluffBets++;
        }
        if (street == Crupier.TURN) {
            stats[seat].turnBets++;
            if (strength <= BotStats.BLUFF_BET_STRENGTH) {
                stats[seat].turnBluffBets++;
            }
        } else if (street == Crupier.RIVER) {
            stats[seat].riverBets++;
            if (strength <= BotStats.BLUFF_BET_STRENGTH) {
                stats[seat].riverBluffBets++;
            }
        }
        trackerFor(players[seat]).recordPostFlopBetOrRaise();
    }

    /**
     * Raw equity of a seat's hand vs one random hand on the current board.
     */
    private double handStrengthOf(int seat) {
        int bs = dealer.getBoardSize();
        int[] board = new int[bs];
        for (int i = 0; i < bs; i++) {
            board[i] = dealer.getBoardCardIndex(i);
        }
        return evaluator.handStrength(
                players[seat].getHoleCard1Index(), players[seat].getHoleCard2Index(), board);
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
        public final double pot;

        public HandResult(Set<Integer> winners, double pot) {
            this.winners = winners;
            this.pot = pot;
        }
    }


}
