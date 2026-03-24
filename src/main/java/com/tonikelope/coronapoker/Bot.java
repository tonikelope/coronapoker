/*
 * Copyright (C) 2020 tonikelope
 * _             _ _        _                 
 * | |_ ___  _ __ (_) | _____| | ___  _ __  ___ 
 * | __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
 * | || (_) | | | | |   <  __/ | (_) | |_) |  __/
 * \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 * ____    ___  ____    ___  
 * |___ \  / _ \|___ \  / _ \ 
 * __) || | | | __) || | | |
 * / __/ | |_| |/ __/ | |_| |
 * |_____| \___/|_____| \___/ 
 *
 * https://github.com/tonikelope/coronapoker
 */
package com.tonikelope.coronapoker;

import java.util.HashMap;
import java.util.Map;

/**
 * FULLY EXPERIMENTAL. B-A-S-E-D on the mythical Alberta's Loki Bot
 * https://poker.cs.ualberta.ca/publications/papp.msc.pdf
 *
 * ADVANCED GTO/EXPLOITATIVE ENGINE - Dynamic Personality Profiles (Shifts based
 * on Stack/M-Ratio) - 169-Hand GTO Preflop Matrix Evaluation - EV-Based
 * Post-Flop Math with Opponent Tracking - Positional Awareness (IP vs OOP
 * Post-flop) - Stack-to-Pot Ratio (SPR) Commitment Logic - Asymmetric Scare
 * Card Reaction & Polarized Sizing
 *
 * @author tonikelope
 */
public class Bot {

    public static final String SUITS = "TDCP";
    public static final int MAX_BET_COUNT = 2;
    public static final int BOT_THINK_TIME = 1500;

    // Core Alberta Engine Tools
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    // Universal Opponent Memory Tracker
    public static final Map<String, OpponentTracker> TRACKER_MEMORY = new HashMap<>();

    public enum Position {
        EARLY, MIDDLE, LATE, BLINDS, UNKNOWN
    }

    public enum Profile {
        NIT, STATION, TAG, LAG
    }

    private volatile RemotePlayer cpuPlayer = null;
    private volatile Profile currentProfile;
    private volatile Profile baseProfile;

    private volatile org.alberta.poker.Card holeCard1 = null;
    private volatile org.alberta.poker.Card holeCard2 = null;
    private volatile int callCount = 0;
    private volatile boolean slowPlay = false;
    private volatile boolean cBetInitiative = false;

    // Delta Analysis
    private volatile double previousStrength = -1.0;
    private volatile int previousStreet = -1;
    private volatile boolean scareCardDetected = false;

    // --- INNER CLASS: OPPONENT TRACKER (VPIP, PFR & AF) ---
    public static class OpponentTracker {

        private int handsPlayed = 0;
        private int voluntarilyPutInPot = 0;
        private int preflopRaises = 0;

        // Post-flop Aggression Factor variables
        private int postFlopBetsAndRaises = 0;
        private int postFlopCalls = 0;

        private int lastVpipHand = -1;
        private int lastPfrHand = -1;

        public void recordHandPlayed() {
            handsPlayed++;
        }

        public void recordVPIP(int handId) {
            if (lastVpipHand != handId) {
                voluntarilyPutInPot++;
                lastVpipHand = handId;
            }
        }

        public void recordPFR(int handId) {
            if (lastPfrHand != handId) {
                preflopRaises++;
                lastPfrHand = handId;
            }
        }

        public void recordPostFlopBetOrRaise() {
            postFlopBetsAndRaises++;
        }

        public void recordPostFlopCall() {
            postFlopCalls++;
        }

        public double getVPIP() {
            return handsPlayed == 0 ? 0 : (double) voluntarilyPutInPot / handsPlayed;
        }

        public double getPFR() {
            return handsPlayed == 0 ? 0 : (double) preflopRaises / handsPlayed;
        }

        // AF = (Bets + Raises) / Calls
        public double getAF() {
            if (postFlopCalls == 0) {
                return postFlopBetsAndRaises > 0 ? 5.0 : 1.0; // Cap at 5.0 (hyper-aggressive)
            }
            return (double) postFlopBetsAndRaises / postFlopCalls;
        }

        public boolean isStation() {
            return handsPlayed > 10 && getVPIP() > 0.45 && getPFR() < 0.10;
        }

        public boolean isNit() {
            return handsPlayed > 10 && getVPIP() < 0.15;
        }

        public boolean isManiac() {
            return handsPlayed > 10 && getVPIP() > 0.40 && getPFR() > 0.30;
        }
    }
    // ------------------------------------------------------

    public Bot(RemotePlayer player) {
        this.cpuPlayer = player;
        assignBaseProfile();
    }

    private void assignBaseProfile() {
        int roll = Helpers.CSPRNG_GENERATOR.nextInt(100);
        if (roll < 20) {
            baseProfile = Profile.NIT;
        } else if (roll < 40) {
            baseProfile = Profile.STATION;
        } else if (roll < 80) {
            baseProfile = Profile.TAG;
        } else {
            baseProfile = Profile.LAG;
        }
        currentProfile = baseProfile;
    }

    private void adjustProfileElasticity() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float stack = cpuPlayer.getStack();
        float blindsCost = dealer.getCiega_grande() + dealer.getCiega_pequeña();
        float mRatio = stack / (blindsCost > 0 ? blindsCost : 1);

        if (mRatio < 12.0f && (baseProfile == Profile.LAG || baseProfile == Profile.STATION)) {
            currentProfile = Profile.TAG; // Push/Fold survival mode
        } else if (mRatio > 60.0f && baseProfile == Profile.NIT) {
            currentProfile = Profile.TAG; // Deep stack looser play
        } else {
            currentProfile = baseProfile;
        }
    }

    public void resetBot() {
        holeCard1 = Bot.coronaCard2LokiCard(cpuPlayer.getHoleCard1());
        holeCard2 = Bot.coronaCard2LokiCard(cpuPlayer.getHoleCard2());

        adjustProfileElasticity();

        slowPlay = Helpers.CSPRNG_GENERATOR.nextInt(100) < (currentProfile == Profile.TAG ? 15 : 5);
        callCount = 0;
        cBetInitiative = false;

        previousStrength = -1.0;
        previousStreet = Crupier.PREFLOP;
        scareCardDetected = false;
    }

    public float getBetSize() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float pot = dealer.getBote_total();
        float currentBet = dealer.getApuesta_actual();
        float minRaise = Helpers.float1DSecureCompare(0f, dealer.getUltimo_raise()) < 0 ? dealer.getUltimo_raise() : dealer.getCiega_grande();
        float bb = dealer.getCiega_grande();

        float targetBet;

        if (dealer.getStreet() == Crupier.PREFLOP) {
            if (dealer.getConta_bet() > 0) {
                targetBet = Helpers.floatClean(minRaise * (currentProfile == Profile.LAG ? 3.5f : 3.0f));
            } else {
                int limpers = dealer.getLimpersCount();
                targetBet = Helpers.floatClean((2.5f + (limpers * 1.5f)) * bb); // Isolate limpers
            }
        } else {
            int textureScore = calculateBoardTexture();
            boolean inPosition = isInPositionPostflop();

            // Texture and Position based sizing
            if (textureScore >= 3) {
                targetBet = pot * (inPosition ? 0.75f : 0.85f); // Protect heavier OOP
            } else if (textureScore == 2) {
                targetBet = pot * 0.55f;
            } else {
                targetBet = pot * 0.33f; // Dry C-Bet
            }

            // Polarized River Overbets
            if (dealer.getStreet() == Crupier.RIVER && (currentProfile == Profile.LAG || currentProfile == Profile.TAG)) {
                if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 15) {
                    targetBet = pot * 1.5f;
                }
            }

            // Gaussian Noise
            if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 10) {
                targetBet += (targetBet * (Helpers.CSPRNG_GENERATOR.nextFloat() * 0.2f - 0.1f));
            }

            targetBet = (float) (Math.ceil(targetBet / GameFrame.CIEGA_PEQUEÑA) * GameFrame.CIEGA_PEQUEÑA);
        }

        if (Helpers.float1DSecureCompare(currentBet, 0f) == 0 || (dealer.getStreet() == Crupier.PREFLOP && Helpers.float1DSecureCompare(currentBet, bb) == 0)) {
            return Math.max(bb, targetBet);
        } else {
            return Math.max(currentBet + minRaise, currentBet + targetBet);
        }
    }

    public int calculateBotDecision(int opponentsCount) {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        int street = dealer.getStreet();
        int activePlayers = dealer.getJugadoresActivos();

        // --------------------------------------------------------
        // PREFLOP PHASE
        // --------------------------------------------------------
        if (street == Crupier.PREFLOP) {
            int decision = calculateGTOPreflopAction();
            if (decision == Player.BET && currentProfile != Profile.STATION) {
                cBetInitiative = true;
            }
            return decision;
        }

        // --------------------------------------------------------
        // POSTFLOP PHASE: EV Engine & Opponent Tracking
        // --------------------------------------------------------
        double strength = HANDEVALUATOR.handRank(holeCard1, holeCard2, Bot.BOT_COMMUNITY_CARDS, opponentsCount);
        double ppot = HANDPOTENTIAL.ppot_raw(holeCard1, holeCard2, Bot.BOT_COMMUNITY_CARDS, true);
        double npot = HANDPOTENTIAL.getLastNPot();
        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        // Asymmetric Scare Card Detection
        if (street != previousStreet) {
            if (previousStrength != -1.0) {
                double delta = effectiveStrength - previousStrength;
                scareCardDetected = (delta < -0.15);
            }
            previousStrength = effectiveStrength;
            previousStreet = street;
        }

        double pot = dealer.getBote_total();
        double callCost = dealer.getApuesta_actual() - cpuPlayer.getBet();
        int betCount = dealer.getConta_bet();
        float remainingStack = cpuPlayer.getStack();

        // Stack-to-Pot Ratio (SPR) Commitment Logic
        float spr = remainingStack / (pot > 0 ? (float) pot : 1.0f);
        boolean potCommitted = false;

        if (callCost > 0) {
            if (spr <= 3.0f && effectiveStrength >= 0.70) {
                potCommitted = true; // Low SPR: Commit with TPTK+
            } else if (spr <= 6.0f && effectiveStrength >= 0.85) {
                potCommitted = true; // Med SPR: Commit with Two Pair+
            } else if (spr > 6.0f && effectiveStrength >= 0.92) {
                potCommitted = true; // High SPR: Commit with Sets+
            }
        }

        boolean inPosition = isInPositionPostflop();
        double winProb = effectiveStrength;

        // Asymmetric Scare Card Reaction
        if (scareCardDetected) {
            if (currentProfile == Profile.NIT || currentProfile == Profile.STATION) {
                winProb -= 0.20; // Passive players fear the board
            } else {
                winProb += 0.05; // Aggressive players see bluff opportunities
            }
        }

        // Adjust for positional disadvantage
        if (!inPosition) {
            winProb -= 0.03;
        }

        // --- POST-FLOP AGGRESSION FACTOR (AF) ADJUSTMENT ---
        OpponentTracker targetStats = getPrimaryOpponentStats();
        double af = targetStats != null ? targetStats.getAF() : 1.0;

        if (betCount > 0 && street > Crupier.PREFLOP) {
            if (af < 0.8) {
                // Opponent is highly passive. A post-flop bet means extreme strength.
                winProb -= 0.15;
            } else if (af > 2.5) {
                // Opponent is a post-flop maniac. Call down lighter (Bluff-catching).
                winProb += 0.10;
            }
        }
        // ---------------------------------------------------

        // RESPECT FOR RAISES (PENALTY) <---
        if (betCount > 1 && street > Crupier.PREFLOP) {
            // If we are facing a raise or re-raise (not just a standard bet)
            // and we have a marginal made hand (strength < 0.75) with weak draws (ppot < 0.20)
            if (effectiveStrength < 0.75 && ppot < 0.20) {
                // Respect the aggression. Marginal hands must fold to raises.
                winProb -= 0.30;
            }
        }
        // ---------------------------------------------

        winProb = Math.max(0.0, Math.min(1.0, winProb));

        // --- IMPLIED ODDS CALCULATOR ---
        // If we are on Flop or Turn, have a strong draw (ppot > 0.15) but a weak made hand (strength < 0.50)
        double impliedPot = pot;

        if (street < Crupier.RIVER && ppot > 0.15 && strength < 0.50) {
            // Estimate how much more we can extract on future streets if we hit our draw.
            double extractionFactor = 0.20;

            // Use the OpponentTracker to adjust how much they will realistically pay us off
            if (targetStats != null) {
                if (targetStats.isStation()) {
                    extractionFactor = 0.40; // Stations can't fold, extract max value
                }
                if (targetStats.isManiac()) {
                    extractionFactor = 0.50;  // Maniacs will aggressively bluff into our nuts
                }
                if (targetStats.isNit()) {
                    extractionFactor = 0.05;     // Nits will fold immediately when the draw hits
                }
            }

            double potentialExtraction = remainingStack * extractionFactor;
            impliedPot += potentialExtraction;
        }
        // -------------------------------

        // Calculate Call EV using the Implied Pot rather than the current raw Pot
        double evCall = (winProb * impliedPot) - ((1.0 - winProb) * callCost);

        // Fold Equity Calculation using Tracker Memory & Position
        double foldEquity = 0.20;

        if (targetStats != null) {
            if (targetStats.isStation()) {
                foldEquity -= 0.15;
            }
            if (targetStats.isNit()) {
                foldEquity += 0.20;
            }
            if (targetStats.isManiac()) {
                foldEquity -= 0.10;
            }
        }

        if (betCount == 0) {
            foldEquity += 0.15;
        }
        if (calculateBoardTexture() >= 3) {
            foldEquity -= 0.10; // Wet boards reduce FE
        }
        if (inPosition) {
            foldEquity += 0.05; // IP bluffing advantage
        }
        foldEquity = Math.max(0, foldEquity / (activePlayers - 1)); // Multi-way dampener

        double raiseAmount = getBetSize();
        double raiseCost = raiseAmount - cpuPlayer.getBet();

        // Calculate Raise EV using Implied Pot (makes semi-bluffing mathematically viable)
        double evRaise = (foldEquity * impliedPot) + ((1.0 - foldEquity) * ((winProb * (impliedPot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        // --------------------------------------------------------
        // DECISION TREE
        // --------------------------------------------------------
        int decision = Player.FOLD;

        // --- PRIORITY 3: PREMEDITATED CHECK-RAISE (TRAPPING MANIACS) ---
        // If we have a monster, are Out of Position, and face an aggressive opponent,
        // we intentionally pass (Check) to induce a bluff, ignoring the random 'slowPlay' flag.
        boolean trappingAggro = (!inPosition && effectiveStrength >= 0.88 && targetStats != null && targetStats.getAF() >= 2.0);
        // ---------------------------------------------------------------

        if (betCount == 0) {
            // Unopened action
            if ((slowPlay || trappingAggro) && effectiveStrength >= 0.88 && street < Crupier.RIVER) {
                decision = Player.CHECK; // Setting the trap
            } else if (street == Crupier.RIVER) {
                // Polarized River Strategy
                if (effectiveStrength > 0.85) {
                    decision = Player.BET; // Value bet
                } else if (effectiveStrength < 0.30 && (currentProfile == Profile.LAG || foldEquity > 0.35)) {
                    decision = Player.BET; // Pure Bluff
                } else {
                    decision = Player.CHECK; // Give up / Showdown
                }
            } else if (cBetInitiative && street == Crupier.FLOP && evRaise > -0.5 && (targetStats == null || !targetStats.isStation())) {
                decision = Player.BET; // Continuation Bet
                cBetInitiative = false;
            } else if (evRaise > 0 && evRaise > evCall) {
                decision = Player.BET;
                callCount++;
            } else {
                decision = Player.CHECK;
            }
        } else {
            // Facing a bet
            cBetInitiative = false;

            if (betCount >= Bot.MAX_BET_COUNT) {
                evRaise = -9999; // Cap raises to avoid infinite loops
            }

            if (potCommitted) {
                decision = Player.CHECK; // Check acts as Call in this engine architecture
                callCount++;
            } else if (evRaise > evCall && evRaise > 0 && effectiveStrength > 0.80 && currentProfile != Profile.STATION) {
                // If we trapped them successfully, this block will trigger the Re-Raise
                decision = Player.BET;
                callCount++;
            } else if (evCall > 0 || ppot > 1.5 * potOdds()) {
                decision = Player.CHECK; // Call based on EV or Implied Potentials
                callCount++;
            } else {
                decision = Player.FOLD;
            }
        }

        return decision;
    }

    /**
     * 13x13 GTO-style Matrix Evaluation (Chen Formula inspired ranges)
     */
    private int calculateGTOPreflopAction() {
        Position pos = determinePosition();
        int betCount = GameFrame.getInstance().getCrupier().getConta_bet();

        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);

        // Range Percentiles: 1 (Top 2%), 2 (Top 5%), 3 (Top 15%), 4 (Top 25%), 5 (Trash)
        int handPercentile = 5;

        if (isPair) {
            if (high >= 9) {
                handPercentile = 1;      // JJ+
            } else if (high >= 6) {
                handPercentile = 2; // 88-TT
            } else {
                handPercentile = 3;                // 22-77
            }
        } else if (high == 12) { // Ace
            if (low >= 10) {
                handPercentile = 1;      // AK, AQ
            } else if (low >= 9) {
                handPercentile = suited ? 1 : 2; // AJ
            } else if (suited) {
                handPercentile = 3;    // A2s-ATs
            } else {
                handPercentile = 4;                // A2o-ATo
            }
        } else if (high == 11) { // King
            if (low >= 10) {
                handPercentile = suited ? 2 : 3; // KQ, KJ
            } else if (suited && low >= 8) {
                handPercentile = 4; // K9s, KTs
            }
        } else if (suited && (high - low <= 2) && high >= 7) {
            handPercentile = 3; // Suited connectors (98s, J9s)
        } else if (high >= 9 && low >= 8) {
            handPercentile = 4; // Broadways (QJ, JT)
        }

        // Action Logic based on position, betCount and Percentile
        if (betCount > 0 && cpuPlayer.getStack() > 0 && ((GameFrame.getInstance().getCrupier().getApuesta_actual() - cpuPlayer.getBet()) > (cpuPlayer.getStack() * 0.20f))) {
            // Facing All-in or huge raise
            if (handPercentile == 1 || (handPercentile == 2 && currentProfile == Profile.LAG)) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

        if (betCount > 0) {
            // Facing standard raise
            if (handPercentile <= 2) {
                return (betCount < Bot.MAX_BET_COUNT && !slowPlay) ? Player.BET : Player.CHECK;
            }
            if (handPercentile == 3 && currentProfile != Profile.NIT) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

        // Open Action
        switch (pos) {
            case EARLY:
                if (handPercentile <= 2) {
                    return Player.BET;
                }
                if (handPercentile == 3 && (currentProfile == Profile.STATION || currentProfile == Profile.LAG)) {
                    return Player.CHECK;
                }
                return Player.FOLD;
            case MIDDLE:
                if (handPercentile <= 3) {
                    return Player.BET;
                }
                if (handPercentile == 4 && currentProfile == Profile.LAG) {
                    return Player.BET;
                }
                return Player.FOLD;
            case LATE:
            case BLINDS: // SB plays similar to Late if unopened, BB will just check if no raise
                if (handPercentile <= 4 && currentProfile != Profile.NIT) {
                    return Player.BET;
                }
                if (pos == Position.BLINDS && betCount == 0) {
                    return Player.CHECK; // BB checks option
                }
                return Player.FOLD;
            default:
                return Player.FOLD;
        }
    }

    private int calculateBoardTexture() {
        if (BOT_COMMUNITY_CARDS.size() < 3) {
            return 0;
        }
        int score = 0;
        int[] suits = new int[4];
        int maxRank = 0;
        int minRank = 14;
        boolean pairOnBoard = false;
        int[] ranks = new int[15];

        for (int i = 1; i <= BOT_COMMUNITY_CARDS.size(); i++) {
            org.alberta.poker.Card c = BOT_COMMUNITY_CARDS.getCard(i);
            suits[c.getSuit()]++;
            ranks[c.getRank()]++;
            if (ranks[c.getRank()] == 2) {
                pairOnBoard = true;
            }
            if (c.getRank() > maxRank) {
                maxRank = c.getRank();
            }
            if (c.getRank() < minRank) {
                minRank = c.getRank();
            }
        }

        for (int s : suits) {
            if (s == 2) {
                score += 1;
            }
            if (s >= 3) {
                score += 3;
            }
        }

        int gap = maxRank - minRank;
        if (gap <= 4) {
            score += 2;
        } else if (gap <= 5) {
            score += 1;
        }

        if (pairOnBoard) {
            score += 1;
        }

        return Math.min(score, 5);
    }

    private Position determinePosition() {
        String myNick = cpuPlayer.getNickname();
        Crupier crupier = GameFrame.getInstance().getCrupier();
        if (myNick.equals(crupier.getUtg_nick())) {
            return Position.EARLY;
        }
        if (myNick.equals(crupier.getDealer_nick())) {
            return Position.LATE;
        }
        if (myNick.equals(crupier.getSb_nick()) || myNick.equals(crupier.getBb_nick())) {
            return Position.BLINDS;
        }
        return Position.MIDDLE;
    }

    private boolean isInPositionPostflop() {
        Position pos = determinePosition();
        return (pos == Position.LATE || pos == Position.MIDDLE);
    }

    private float potOdds() {
        Crupier d = GameFrame.getInstance().getCrupier();
        float toCall = d.getApuesta_actual() - cpuPlayer.getBet();
        return toCall / (d.getBote_total() + toCall);
    }

    private OpponentTracker getPrimaryOpponentStats() {
        Player lastAggressor = GameFrame.getInstance().getCrupier().getLast_aggressor();
        if (lastAggressor != null && TRACKER_MEMORY.containsKey(lastAggressor.getNickname())) {
            return TRACKER_MEMORY.get(lastAggressor.getNickname());
        }
        return null;
    }

    // API Converters
    public static int coronaCardSuit2LokiCardSuit(Card carta) {
        return Bot.SUITS.indexOf(carta.getPalo());
    }

    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(int carta) {
        int v = (carta - 1) % 13;
        int val = (v == 0 ? 14 : v + 1);
        String palo = Card.PALOS[(int) ((float) (carta - 1) / 13)];
        return new org.alberta.poker.Card(val - 2, Bot.SUITS.indexOf(palo));
    }

    public static org.alberta.poker.Card coronaCard2LokiCard(Card carta) {
        return new org.alberta.poker.Card(carta.getValorNumerico() - 2, Bot.coronaCardSuit2LokiCardSuit(carta));
    }
}
