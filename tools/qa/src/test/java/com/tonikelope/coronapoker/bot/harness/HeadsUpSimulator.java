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
import java.util.Arrays;
import java.util.Random;

/**
 * Minimal heads-up no-limit Texas hold'em simulator that drives two {@link Bot}
 * instances through complete hands without touching Swing or GameFrame state.
 *
 * The simulator is intentionally compact: it implements ordinary betting flow
 * (post blinds, action order, fold/call/raise resolution, all-in clamp,
 * showdown) but skips features that do not affect bot decision quality
 * measurement: side pots, run-it-twice, time-banks, GUI animations.
 *
 * Designed so a JUnit test can run thousands of hands in well under a second
 * and read the result.
 */
public final class HeadsUpSimulator {

    static {
        // Bootstrap statics the bot reads in production code.
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        // Small-blind rounding unit used by Bot.getBetSize.
        GameFrame.CIEGA_PEQUEÑA = 1.0f;
    }

    /** Maximum number of decisions per street. Safety net against infinite loops. */
    private static final int MAX_ACTIONS_PER_STREET = 12;

    private final TestBotPlayer p1;
    private final TestBotPlayer p2;
    private Bot bot1;
    private Bot bot2;
    private final TestDealer dealer;
    private final BotEvaluator evaluator;
    private final Random deckRng;
    private final float bigBlind;
    private final float smallBlind;
    private final float startingStack;

    // Per-bot aggregate stats (incremented as decisions stream in).
    private final BotStats statsA = new BotStats("BOT_A");
    private final BotStats statsB = new BotStats("BOT_B");

    // Per-hand transient flags for VPIP / PFR / c-bet detection.
    private boolean handVoluntaryA, handVoluntaryB;
    private boolean handRaisedPreflopA, handRaisedPreflopB;
    private boolean handCbetOppA, handCbetOppB;
    private boolean handCbetDoneA, handCbetDoneB;

    // Hand counter for OpponentTracker handId — mirrors Crupier.conta_mano so
    // tracker.recordVPIP/recordPFR detect repeated actions inside a single
    // hand and do not double-count.
    private int handCounter = 0;

    public HeadsUpSimulator(long seed, float startingStack, float bigBlind, BotEvaluator evaluator) {
        this.deckRng = new Random(seed);
        this.bigBlind = bigBlind;
        this.smallBlind = bigBlind / 2.0f;
        this.startingStack = startingStack;
        this.evaluator = evaluator;
        this.p1 = new TestBotPlayer("BOT_A", startingStack);
        this.p2 = new TestBotPlayer("BOT_B", startingStack);
        this.dealer = new TestDealer();
        this.dealer.setBlinds(smallBlind, bigBlind);
        this.dealer.setSeating(Arrays.asList(p1, p2));
        this.bot1 = new Bot(p1);
        this.bot2 = new Bot(p2);
        this.bot1.setContext(dealer, evaluator);
        this.bot2.setContext(dealer, evaluator);
        // Distinct seeded RNGs per bot so simulation runs are reproducible
        this.bot1.setRng(new Random(seed ^ 0x1L));
        this.bot2.setRng(new Random(seed ^ 0x2L));
    }

    public TestBotPlayer playerA() {
        return p1;
    }

    public TestBotPlayer playerB() {
        return p2;
    }

    public Bot botA() {
        return bot1;
    }

    public Bot botB() {
        return bot2;
    }

    public TestDealer dealerView() {
        return dealer;
    }

    /**
     * Replace bot at seat A with an externally constructed instance (e.g. a
     * {@link FixedStrategyBot} benchmark). The new bot must already be bound
     * to {@link #playerA()}; this method wires up the dealer context.
     */
    public void setBotA(Bot bot) {
        this.bot1 = bot;
        this.bot1.setContext(dealer, evaluator);
    }

    public void setBotB(Bot bot) {
        this.bot2 = bot;
        this.bot2.setContext(dealer, evaluator);
    }

    public BotStats statsA() {
        return statsA;
    }

    public BotStats statsB() {
        return statsB;
    }

    /**
     * Assign per-bot difficulty for mixed matchups (e.g. EXPERT vs EASY in the
     * same hand). Forces personality re-roll for both bots.
     */
    public void setBotDifficulties(Bot.Difficulty diffA, Bot.Difficulty diffB) {
        bot1.setDifficulty(diffA);
        bot2.setDifficulty(diffB);
    }

    /**
     * Top up both stacks back to the configured starting amount. Useful so
     * each hand is measured in isolation (constant effective stack).
     */
    public void resetStacks() {
        p1.setStack(startingStack);
        p2.setStack(startingStack);
    }

    /**
     * Play one heads-up hand to completion.
     *
     * @param aIsButton if true, player A posts the small blind (acts first
     *                  preflop and last postflop); if false, player B is the
     *                  button.
     * @return result with the winning index (0=A, 1=B, -1=tie) and pot size.
     */
    public HandResult playOneHand(boolean aIsButton) {
        int[] deck = shuffledDeck();

        p1.setHoleCards(deck[0], deck[2]);
        p2.setHoleCards(deck[1], deck[3]);
        int[] runout = {deck[4], deck[5], deck[6], deck[7], deck[8]};

        p1.setActivo(true);
        p2.setActivo(true);
        p1.setBet(0f);
        p2.setBet(0f);
        dealer.resetBoard();

        // Reset per-hand stat flags
        handVoluntaryA = false;
        handVoluntaryB = false;
        handRaisedPreflopA = false;
        handRaisedPreflopB = false;
        handCbetOppA = false;
        handCbetOppB = false;
        handCbetDoneA = false;
        handCbetDoneB = false;

        // Capture stacks before posting blinds so we can compute net delta later
        float aStartStack = p1.getStack();
        float bStartStack = p2.getStack();

        TestBotPlayer sbPlayer = aIsButton ? p1 : p2;
        TestBotPlayer bbPlayer = aIsButton ? p2 : p1;
        Bot sbBot = aIsButton ? bot1 : bot2;
        Bot bbBot = aIsButton ? bot2 : bot1;

        dealer.setRoles(sbPlayer.getNickname(), sbPlayer.getNickname(),
                bbPlayer.getNickname(), sbPlayer.getNickname());
        dealer.setStreet(Crupier.PREFLOP);

        chargeBet(sbPlayer, smallBlind);
        chargeBet(bbPlayer, bigBlind);
        dealer.setPot(smallBlind + bigBlind);
        dealer.setCurrentBet(bigBlind);
        dealer.setLastRaise(bigBlind);
        // betCount tracks voluntary raises above the BB, so blinds posted = 0.
        // Without this, the SB sees "betCount==1" and the bot treats every preflop
        // decision as facing a 3-bet, collapsing PFR to ~0% across all difficulties.
        dealer.setBetCount(0);
        dealer.setLimpersCount(0);
        dealer.setLastAggressor(null);

        sbBot.resetBot();
        bbBot.resetBot();

        // Tracker: record hand played for each active opponent. Mirrors
        // Crupier.java line 3443. Without this the opponent tracker stays
        // empty and the bot never identifies stations/nits/maniacs.
        handCounter++;
        trackerFor(p1).recordHandPlayed();
        trackerFor(p2).recordHandPlayed();

        boolean handReachedShowdown = false;
        int winnerIndex = -1;

        for (int street = Crupier.PREFLOP; street <= Crupier.RIVER; street++) {
            dealer.setStreet(street);

            if (street > Crupier.PREFLOP) {
                openStreet(runout, street);
                if (street == Crupier.FLOP) {
                    // Mark c-bet opportunity for whoever held preflop initiative
                    handCbetOppA = handRaisedPreflopA;
                    handCbetOppB = handRaisedPreflopB;
                }
            }

            Bot firstBot = (street == Crupier.PREFLOP) ? sbBot : bbBot;
            TestBotPlayer firstPlayer = (street == Crupier.PREFLOP) ? sbPlayer : bbPlayer;
            Bot secondBot = (street == Crupier.PREFLOP) ? bbBot : sbBot;
            TestBotPlayer secondPlayer = (street == Crupier.PREFLOP) ? bbPlayer : sbPlayer;

            BettingResult result = runBettingRound(firstBot, firstPlayer, secondBot, secondPlayer);
            if (result == BettingResult.HAND_OVER_FOLD) {
                TestBotPlayer winner = firstPlayer.isActivo() ? firstPlayer : secondPlayer;
                float pot = dealer.getBote_total();
                winner.setStack(winner.getStack() + pot);
                winnerIndex = (winner == p1) ? 0 : 1;
                finalizeHandStats(aStartStack, bStartStack, false, winnerIndex);
                return new HandResult(winnerIndex, pot);
            }
            if (!p1.isActivo() || !p2.isActivo()) {
                break;
            }
            if (p1.getStack() <= 0 && p2.getStack() <= 0) {
                while (dealer.getBoardSize() < 5) {
                    int nextIdx = Math.min(dealer.getBoardSize() + 1, 4);
                    dealer.appendBoardCard(runout[nextIdx]);
                }
                break;
            }
        }

        HandResult sd = showdown();
        handReachedShowdown = true;
        finalizeHandStats(aStartStack, bStartStack, handReachedShowdown, sd.winnerIndex);
        return sd;
    }

    private void finalizeHandStats(float aStartStack, float bStartStack,
                                   boolean reachedShowdown, int winnerIndex) {
        statsA.handsPlayed++;
        statsB.handsPlayed++;
        statsA.netChipsWon += (p1.getStack() - aStartStack);
        statsB.netChipsWon += (p2.getStack() - bStartStack);
        if (handVoluntaryA) {
            statsA.handsVoluntaryMoneyPreflop++;
        }
        if (handVoluntaryB) {
            statsB.handsVoluntaryMoneyPreflop++;
        }
        if (handRaisedPreflopA) {
            statsA.handsWithPreflopRaise++;
        }
        if (handRaisedPreflopB) {
            statsB.handsWithPreflopRaise++;
        }
        if (handCbetOppA) {
            statsA.cbetOpportunities++;
            if (handCbetDoneA) {
                statsA.cbetExecuted++;
            }
        }
        if (handCbetOppB) {
            statsB.cbetOpportunities++;
            if (handCbetDoneB) {
                statsB.cbetExecuted++;
            }
        }
        if (reachedShowdown) {
            statsA.handsReachedShowdown++;
            statsB.handsReachedShowdown++;
        }
        if (winnerIndex == 0) {
            statsA.handsWon++;
            if (reachedShowdown) {
                statsA.handsWonAtShowdown++;
            }
        } else if (winnerIndex == 1) {
            statsB.handsWon++;
            if (reachedShowdown) {
                statsB.handsWonAtShowdown++;
            }
        } else if (winnerIndex == -1 && reachedShowdown) {
            // Tie: count half a showdown win each
            statsA.handsWonAtShowdown++;
            statsB.handsWonAtShowdown++;
        }
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
        p1.setBet(0f);
        p2.setBet(0f);
    }

    private BettingResult runBettingRound(Bot firstBot, TestBotPlayer firstPlayer,
                                          Bot secondBot, TestBotPlayer secondPlayer) {
        Bot toActBot = firstBot;
        TestBotPlayer toActPlayer = firstPlayer;
        Bot waitingBot = secondBot;
        TestBotPlayer waitingPlayer = secondPlayer;

        int actions = 0;
        while (actions < MAX_ACTIONS_PER_STREET) {
            if (!toActPlayer.isActivo()) {
                return BettingResult.HAND_OVER_FOLD;
            }
            if (toActPlayer.getStack() <= 0f) {
                actions++;
                if (actions >= 2 && bothMatched()) {
                    return BettingResult.STREET_DONE;
                }
                Bot tmpB = toActBot; toActBot = waitingBot; waitingBot = tmpB;
                TestBotPlayer tmpP = toActPlayer; toActPlayer = waitingPlayer; waitingPlayer = tmpP;
                continue;
            }

            int decision = toActBot.calculateBotDecision(1);
            int street = dealer.getStreet();
            float currentBet = dealer.getApuesta_actual();
            float toCall = currentBet - toActPlayer.getBet();
            boolean actorIsA = toActPlayer == p1;

            if (decision == Player.FOLD) {
                toActPlayer.setActivo(false);
                if (street > Crupier.PREFLOP) {
                    recordPostflopFold(actorIsA);
                }
                return BettingResult.HAND_OVER_FOLD;
            } else if (decision == Player.CHECK) {
                if (toCall > 0f) {
                    float pay = Math.min(toCall, toActPlayer.getStack());
                    chargeBet(toActPlayer, pay);
                    dealer.setPot(dealer.getBote_total() + pay);
                    if (street == Crupier.PREFLOP) {
                        recordPreflopVoluntary(actorIsA);
                    } else {
                        recordPostflopCall(actorIsA);
                    }
                } else if (street > Crupier.PREFLOP) {
                    recordPostflopCheck(actorIsA);
                }
                actions++;
                if (actions >= 2 && bothMatched()) {
                    return BettingResult.STREET_DONE;
                }
            } else if (decision == Player.BET) {
                float newBet = toActBot.getBetSize();
                float maxAffordable = toActPlayer.getBet() + toActPlayer.getStack();
                if (newBet > maxAffordable) {
                    newBet = maxAffordable;
                }
                if (newBet <= currentBet) {
                    if (toCall > 0f) {
                        float pay = Math.min(toCall, toActPlayer.getStack());
                        chargeBet(toActPlayer, pay);
                        dealer.setPot(dealer.getBote_total() + pay);
                        if (street == Crupier.PREFLOP) {
                            recordPreflopVoluntary(actorIsA);
                        } else {
                            recordPostflopCall(actorIsA);
                        }
                    }
                } else {
                    float raiseSize = newBet - currentBet;
                    float pay = newBet - toActPlayer.getBet();
                    pay = Math.min(pay, toActPlayer.getStack());
                    chargeBet(toActPlayer, pay);
                    dealer.setPot(dealer.getBote_total() + pay);
                    dealer.setCurrentBet(toActPlayer.getBet());
                    dealer.setLastRaise(raiseSize);
                    dealer.setBetCount(dealer.getConta_bet() + 1);
                    dealer.setLastAggressor(toActPlayer);

                    if (street == Crupier.PREFLOP) {
                        recordPreflopRaise(actorIsA);
                    } else {
                        recordPostflopBetRaise(actorIsA, street, toCall);
                    }
                }
                actions++;
                if (actions >= 2 && bothMatched()) {
                    return BettingResult.STREET_DONE;
                }
            } else {
                actions++;
            }

            Bot tmpB = toActBot; toActBot = waitingBot; waitingBot = tmpB;
            TestBotPlayer tmpP = toActPlayer; toActPlayer = waitingPlayer; waitingPlayer = tmpP;
        }
        return BettingResult.STREET_DONE;
    }

    // --- stat update helpers -------------------------------------------------

    private Bot.OpponentTracker trackerFor(TestBotPlayer p) {
        return Bot.TRACKER_MEMORY.computeIfAbsent(p.getNickname(),
                k -> new Bot.OpponentTracker());
    }

    private void recordPreflopVoluntary(boolean actorIsA) {
        if (actorIsA) {
            handVoluntaryA = true;
        } else {
            handVoluntaryB = true;
        }
        trackerFor(actorIsA ? p1 : p2).recordVPIP(handCounter);
    }

    private void recordPreflopRaise(boolean actorIsA) {
        if (actorIsA) {
            handVoluntaryA = true;
            handRaisedPreflopA = true;
            // The new raiser displaces any previous aggressor
            handRaisedPreflopB = false;
        } else {
            handVoluntaryB = true;
            handRaisedPreflopB = true;
            handRaisedPreflopA = false;
        }
        Bot.OpponentTracker t = trackerFor(actorIsA ? p1 : p2);
        t.recordVPIP(handCounter);
        t.recordPFR(handCounter);
    }

    private void recordPostflopBetRaise(boolean actorIsA, int street, float toCall) {
        BotStats s = actorIsA ? statsA : statsB;
        s.postflopBetsRaises++;
        // Classify the aggressive action by the made-hand strength behind it
        // (value vs bluff) so the harness can measure how readable the bot is.
        double strength = handStrengthOf(actorIsA ? p1 : p2);
        if (strength >= BotStats.VALUE_BET_STRENGTH) {
            s.postflopValueBets++;
        } else if (strength <= BotStats.BLUFF_BET_STRENGTH) {
            s.postflopBluffBets++;
        }
        if (street == Crupier.TURN) {
            s.turnBets++;
            if (strength <= BotStats.BLUFF_BET_STRENGTH) {
                s.turnBluffBets++;
            }
        } else if (street == Crupier.RIVER) {
            s.riverBets++;
            if (strength <= BotStats.BLUFF_BET_STRENGTH) {
                s.riverBluffBets++;
            }
        }
        // C-bet detection: flop, first action by preflop aggressor, no bet yet faced
        if (street == Crupier.FLOP && toCall == 0f) {
            if (actorIsA && handCbetOppA && !handCbetDoneA) {
                handCbetDoneA = true;
            } else if (!actorIsA && handCbetOppB && !handCbetDoneB) {
                handCbetDoneB = true;
            }
        }
        trackerFor(actorIsA ? p1 : p2).recordPostFlopBetOrRaise();
    }

    /** Raw equity of a player's hand vs one random hand on the current board. */
    private double handStrengthOf(TestBotPlayer p) {
        int bs = dealer.getBoardSize();
        int[] board = new int[bs];
        for (int i = 0; i < bs; i++) {
            board[i] = dealer.getBoardCardIndex(i);
        }
        return evaluator.handStrength(p.getHoleCard1Index(), p.getHoleCard2Index(), board);
    }

    private void recordPostflopCall(boolean actorIsA) {
        (actorIsA ? statsA : statsB).postflopCalls++;
        trackerFor(actorIsA ? p1 : p2).recordPostFlopCall();
    }

    private void recordPostflopCheck(boolean actorIsA) {
        (actorIsA ? statsA : statsB).postflopChecks++;
    }

    private void recordPostflopFold(boolean actorIsA) {
        (actorIsA ? statsA : statsB).postflopFolds++;
    }

    private boolean bothMatched() {
        float c = dealer.getApuesta_actual();
        return Math.abs(p1.getBet() - c) < 0.0001f && Math.abs(p2.getBet() - c) < 0.0001f;
    }

    private void chargeBet(TestBotPlayer p, float amount) {
        p.setStack(p.getStack() - amount);
        p.setBet(p.getBet() + amount);
    }

    private HandResult showdown() {
        int boardSize = dealer.getBoardSize();
        int[] hand1 = new int[2 + boardSize];
        int[] hand2 = new int[2 + boardSize];
        hand1[0] = p1.getHoleCard1Index();
        hand1[1] = p1.getHoleCard2Index();
        hand2[0] = p2.getHoleCard1Index();
        hand2[1] = p2.getHoleCard2Index();
        for (int i = 0; i < boardSize; i++) {
            hand1[2 + i] = dealer.getBoardCardIndex(i);
            hand2[2 + i] = dealer.getBoardCardIndex(i);
        }

        int cmp = evaluator.compareHands(hand1, hand2);
        float pot = dealer.getBote_total();
        if (cmp == 1) {
            p1.setStack(p1.getStack() + pot);
            return new HandResult(0, pot);
        } else if (cmp == 2) {
            p2.setStack(p2.getStack() + pot);
            return new HandResult(1, pot);
        } else {
            p1.setStack(p1.getStack() + pot / 2f);
            p2.setStack(p2.getStack() + pot / 2f);
            return new HandResult(-1, pot);
        }
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

        /** 0 = player A wins, 1 = player B wins, -1 = tie. */
        public final int winnerIndex;
        public final float pot;

        public HandResult(int winnerIndex, float pot) {
            this.winnerIndex = winnerIndex;
            this.pot = pot;
        }
    }

    private enum BettingResult {
        STREET_DONE,
        HAND_OVER_FOLD
    }
}
