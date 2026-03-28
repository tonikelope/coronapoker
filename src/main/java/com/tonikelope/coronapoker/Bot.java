/*
 * Copyright (C) 2020 tonikelope
 * _              _ _        _                 
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUMANIZED EXPLOITATIVE ENGINE Balanced for a fun, realistic club-level poker
 * experience. Features softened MDF (fear factor), reduced geometric pressure,
 * and heavily weighted TAG profiles.
 *
 * @author tonikelope
 */
public class Bot {

    public static final String SUITS = "TDCP";
    public static final int MAX_BET_COUNT = 3;
    public static final int BOT_THINK_TIME = 1500;

    // Core Alberta Engine Tools (Public static final for Crupier compatibility)
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    // Universal Opponent Memory Tracker using ConcurrentHashMap for thread safety
    public static final Map<String, OpponentTracker> TRACKER_MEMORY = new ConcurrentHashMap<>();

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

    // Delta Analysis & Bayesian Memory
    private volatile double previousStrength = -1.0;
    private volatile int previousStreet = -1;
    private volatile boolean scareCardDetected = false;
    private volatile boolean highActionPot = false;

    // --- INNER CLASS: OPPONENT TRACKER ---
    public static class OpponentTracker {

        private int handsPlayed = 0;
        private int voluntarilyPutInPot = 0;
        private int preflopRaises = 0;
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

        public double getAF() {
            if (postFlopCalls == 0) {
                return postFlopBetsAndRaises > 0 ? 5.0 : 1.0;
            }
            return Math.min(5.0, (double) postFlopBetsAndRaises / postFlopCalls);
        }

        public boolean isStation() {
            return handsPlayed > 10 && getVPIP() > 0.45 && getPFR() < 0.10;
        }

        public boolean isNit() {
            return handsPlayed > 10 && getVPIP() < 0.15;
        }

        public boolean isManiac() {
            return handsPlayed > 10 && getVPIP() > 0.40 && getPFR() > 0.25 && getAF() > 2.0;
        }
    }

    public Bot(RemotePlayer player) {
        this.cpuPlayer = player;
        assignBaseProfile();
    }

    private void assignBaseProfile() {
        int roll = Helpers.CSPRNG_GENERATOR.nextInt(100);
        // More human distribution: mostly solid players, a few stations, rare maniacs/nits
        if (roll < 10) {
            baseProfile = Profile.NIT; // 10% Rock
        } else if (roll < 35) {
            baseProfile = Profile.STATION; // 25% Calling Station (fun to value bet)
        } else if (roll < 90) {
            baseProfile = Profile.TAG; // 55% Solid Standard
        } else {
            baseProfile = Profile.LAG; // 10% Aggressive
        }
        currentProfile = baseProfile;
    }

    private void adjustProfileElasticity() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float stack = cpuPlayer.getStack();
        float blindsCost = dealer.getCiega_grande() + dealer.getCiega_pequeña();
        float mRatio = stack / (blindsCost > 0 ? blindsCost : 1);

        if (mRatio < 12.0f && (baseProfile == Profile.LAG || baseProfile == Profile.STATION)) {
            currentProfile = Profile.TAG;
        } else if (mRatio > 60.0f && baseProfile == Profile.NIT) {
            currentProfile = Profile.TAG;
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
        highActionPot = false;
    }

    public float getBetSize() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float pot = dealer.getBote_total();
        float currentBet = dealer.getApuesta_actual();
        float minRaise = Helpers.float1DSecureCompare(0f, dealer.getUltimo_raise()) < 0 ? dealer.getUltimo_raise() : dealer.getCiega_grande();
        float bb = dealer.getCiega_grande();
        float targetBet;

        OpponentTracker targetStats = getPrimaryOpponentStats();

        if (dealer.getStreet() == Crupier.PREFLOP) {
            if (dealer.getConta_bet() > 0) {
                // Standard 3-bet sizing
                boolean oop = !isInPositionPostflop();
                targetBet = minRaise * (oop ? 3.5f : 3.0f);
            } else {
                int limpers = dealer.getLimpersCount();
                targetBet = (2.5f + (limpers * 1.5f)) * bb;
            }
        } else {
            int textureScore = calculateBoardTexture();
            boolean inPosition = isInPositionPostflop();

            // Relaxed sizing: less suffocating geometric pressure, more standard poker fractions
            if (targetStats != null && targetStats.isStation()) {
                targetBet = pot * 0.80f; // Solid value against stations
            } else {
                if (textureScore >= 3) {
                    targetBet = pot * (inPosition ? 0.60f : 0.75f); // Protect hands but don't over-commit
                } else if (textureScore == 2) {
                    targetBet = pot * 0.50f; // Half pot standard
                } else {
                    targetBet = pot * 0.33f; // Dry board C-Bet
                }
            }

            // Occasional polarized River bets (human unpredictability)
            if (dealer.getStreet() == Crupier.RIVER && currentProfile == Profile.LAG) {
                if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 10) {
                    targetBet = pot * 1.25f; // Slight overbet, not crazy 1.5x
                }
            }
        }

        // Gaussian Noise for varied sizing
        if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 15) {
            targetBet += (targetBet * (Helpers.CSPRNG_GENERATOR.nextFloat() * 0.2f - 0.1f));
        }

        targetBet = (float) (Math.ceil(Helpers.floatClean(targetBet) / GameFrame.CIEGA_PEQUEÑA) * GameFrame.CIEGA_PEQUEÑA);

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
        int betCount = dealer.getConta_bet();

        if (betCount > 1) {
            highActionPot = true;
        }

        if (street == Crupier.PREFLOP) {
            int decision = calculateGTOPreflopAction();
            if (decision == Player.BET && currentProfile != Profile.STATION) {
                cBetInitiative = true;
            }
            return decision;
        }

        double strength = HANDEVALUATOR.handRank(holeCard1, holeCard2, BOT_COMMUNITY_CARDS, opponentsCount);
        double ppot = HANDPOTENTIAL.ppot_raw(holeCard1, holeCard2, BOT_COMMUNITY_CARDS, true);
        double npot = HANDPOTENTIAL.getLastNPot();
        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        if (street != previousStreet) {
            if (previousStrength != -1.0) {
                scareCardDetected = ((effectiveStrength - previousStrength) < -0.15);
            }
            previousStrength = effectiveStrength;
            previousStreet = street;
        }

        double pot = dealer.getBote_total();
        double callCost = dealer.getApuesta_actual() - cpuPlayer.getBet();
        float spr = cpuPlayer.getStack() / (pot > 0 ? (float) pot : 1.0f);
        boolean potCommitted = false;

        float multiWayFactor = (activePlayers > 2) ? 0.08f * activePlayers : 0f;

        if (callCost > 0) {
            // slightly higher commitment thresholds so the bot doesn't stack off lightly
            if (spr <= 3.0f && effectiveStrength >= (0.75 + multiWayFactor)) {
                potCommitted = true;
            } else if (spr <= 6.0f && effectiveStrength >= (0.88 + multiWayFactor)) {
                potCommitted = true;
            } else if (spr > 6.0f && effectiveStrength >= (0.95 + multiWayFactor)) {
                potCommitted = true;
            }
        }

        boolean inPosition = isInPositionPostflop();
        double winProb = effectiveStrength;

        // Softened Bayesian reduction
        if (highActionPot && street >= Crupier.FLOP) {
            winProb -= 0.10;
        }

        if (activePlayers > 2) {
            winProb -= (activePlayers * 0.05);
        }

        if (scareCardDetected) {
            winProb += (currentProfile == Profile.NIT || currentProfile == Profile.STATION) ? -0.25 : 0.00;
        }
        if (!inPosition) {
            winProb -= 0.03;
        }

        OpponentTracker targetStats = getPrimaryOpponentStats();

        if (betCount > 0 && street > Crupier.PREFLOP && targetStats != null) {
            double af = targetStats.getAF();
            double vpip = targetStats.getVPIP();
            if (af < 0.8) {
                winProb -= 0.15;
            } else if (af > 2.5 && vpip < 0.15) {
                winProb -= 0.20;
            } else if (af > 2.5) {
                winProb += 0.05;
            }
        }

        winProb = Math.max(0.0, Math.min(1.0, winProb));
        double impliedPot = pot;

        boolean safeDraw = (npot < 0.15);

        if (street < Crupier.RIVER && ppot > 0.15 && strength < 0.50 && safeDraw) {
            double extractionFactor = 0.15; // More conservative implied odds
            if (targetStats != null) {
                if (targetStats.isStation()) {
                    extractionFactor = 0.35;
                } else if (targetStats.isNit()) {
                    extractionFactor = 0.05;
                }
            }
            impliedPot += (cpuPlayer.getStack() * extractionFactor);
        }

        double evCall = (winProb * impliedPot) - ((1.0 - winProb) * callCost);
        double foldEquity = 0.20;

        if (targetStats != null) {
            if (targetStats.isStation()) {
                foldEquity -= 0.20;
            } else if (targetStats.isNit()) {
                foldEquity += 0.15;
            }
        }

        double raiseAmount = getBetSize();
        double betToPotRatio = raiseAmount / (pot > 0 ? pot : 1);
        foldEquity += (betToPotRatio * 0.10);

        if (betCount == 0) {
            foldEquity += 0.15;
        }
        if (calculateBoardTexture() >= 3) {
            foldEquity -= 0.10;
        }
        if (inPosition) {
            foldEquity += 0.05;
        }

        foldEquity = Math.max(0, Math.min(0.75, foldEquity / Math.max(1, activePlayers - 1)));
        double raiseCost = raiseAmount - cpuPlayer.getBet();
        double evRaise = (foldEquity * impliedPot) + ((1.0 - foldEquity) * ((winProb * (impliedPot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        int decision = Player.FOLD;
        boolean trappingAggro = (!inPosition && effectiveStrength >= 0.88 && targetStats != null && targetStats.getAF() >= 2.0);

        if (betCount == 0) {
            if ((slowPlay || trappingAggro) && effectiveStrength >= 0.88 && street < Crupier.RIVER) {
                decision = Player.CHECK;
            } else if (street == Crupier.RIVER) {
                if (effectiveStrength >= 0.70) { // Safer thin value
                    decision = Player.BET;
                } else if (effectiveStrength < 0.30 && foldEquity > 0.40) {
                    boolean holdsBlocker = (holeCard1.getRank() >= 10 || holeCard2.getRank() >= 10);
                    // Bluffs are slightly less frequent to feel more recreational
                    boolean feelsLikeBluffing = Helpers.CSPRNG_GENERATOR.nextInt(100) < 60;
                    decision = (holdsBlocker && feelsLikeBluffing) ? Player.BET : Player.CHECK;
                } else {
                    decision = Player.CHECK;
                }
            } else if (cBetInitiative && street == Crupier.FLOP && evRaise > -0.5 && (targetStats == null || !targetStats.isStation())) {
                decision = Player.BET;
                cBetInitiative = false;
            } else if (evRaise > 0 && evRaise > evCall) {
                decision = Player.BET;
                callCount++;
            } else {
                decision = Player.CHECK;
            }
        } else {
            cBetInitiative = false;
            if (betCount >= Bot.MAX_BET_COUNT) {
                evRaise = -9999;
            }

            // HUMAN FEAR FACTOR (Softened MDF)
            // Humans over-fold. We reduce the required defense threshold by 25%.
            double mathMdf = pot / (pot + callCost);
            double humanMdf = mathMdf * 0.75;
            boolean passesHumanMdf = (winProb >= (1.0 - humanMdf));

            if (potCommitted) {
                decision = Player.CHECK;
                callCount++;
            } else if (evRaise > evCall && evRaise > 0 && effectiveStrength > 0.85 && currentProfile != Profile.STATION) {
                decision = Player.BET;
                callCount++;
            } else if ((evCall > 0 || passesHumanMdf) && safeDraw) {
                decision = Player.CHECK;
                callCount++;
            } else if (ppot > 1.5 * potOdds() && safeDraw) {
                decision = Player.CHECK;
                callCount++;
            } else {
                decision = Player.FOLD;
            }
        }

        return decision;
    }

    private int calculateGTOPreflopAction() {
        Position pos = determinePosition();
        int betCount = GameFrame.getInstance().getCrupier().getConta_bet();

        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);

        int handPercentile = 5;

        if (isPair) {
            if (high >= 9) {
                handPercentile = 1;
            } else if (high >= 6) {
                handPercentile = 2;
            } else {
                handPercentile = 3;
            }
        } else if (high == 12) {
            if (low >= 10) {
                handPercentile = 1;
            } else if (low >= 9) {
                handPercentile = suited ? 1 : 2;
            } else if (suited) {
                handPercentile = 3;
            } else {
                handPercentile = 4;
            }
        } else if (high == 11) {
            if (low >= 10) {
                handPercentile = suited ? 2 : 3;
            } else if (suited && low >= 8) {
                handPercentile = 4;
            }
        } else if (suited && (high - low <= 2) && high >= 7) {
            handPercentile = 3;
        } else if (high >= 9 && low >= 8) {
            handPercentile = 4;
        }

        // SOFTENED 3-BETTING
        // Less robotic bluffs. Humans don't always 3-bet A5s unless they feel creative.
        if (betCount == 1) {
            boolean isMonster = (isPair && high >= 10) || (high == 12 && low == 11); // QQ+, AK
            boolean isSuitedWheelAce = (high == 12 && suited && low <= 3);

            boolean feelsCreative = Helpers.CSPRNG_GENERATOR.nextInt(100) < 25; // Only 25% of the time

            if (isMonster || (isSuitedWheelAce && feelsCreative)) {
                return Player.BET;
            }
        }

        if (betCount > 1 && cpuPlayer.getStack() > 0 && ((GameFrame.getInstance().getCrupier().getApuesta_actual() - cpuPlayer.getBet()) > (cpuPlayer.getStack() * 0.20f))) {
            if (handPercentile == 1) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

        if (betCount > 0) {
            if (handPercentile <= 2) {
                return (betCount < Bot.MAX_BET_COUNT && !slowPlay) ? Player.BET : Player.CHECK;
            }
            if (handPercentile == 3 && currentProfile != Profile.NIT) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

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
            case BLINDS:
                if (betCount == 0) {
                    if (handPercentile <= 3 && currentProfile != Profile.NIT) {
                        return Player.BET;
                    }
                    return Player.CHECK;
                }
            case LATE:
                if (handPercentile <= 4 && currentProfile != Profile.NIT) {
                    return Player.BET;
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
