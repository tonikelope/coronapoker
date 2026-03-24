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
 * ADVANCED GTO/EXPLOITATIVE ENGINE
 * - Dynamic Personality Profiles (Shifts based on Stack/M-Ratio)
 * - Positional Preflop Charts & Isolation Sizing
 * - EV-Based Post-Flop Math with Opponent Tracking
 * - Asymmetric Scare Card Reaction (Exploitative Bluffing)
 * - Polarized River Overbets
 *
 * @author tonikelope
 */
public class Bot {

    public static final String SUITS = "TDCP";
    public static final int MAX_BET_COUNT = 2;
    public static final int BOT_THINK_TIME = 1500;
    
    // Core Alberta Engine Tools (Names matched for Crupier compatibility)
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    // Universal Opponent Memory Tracker
    public static final Map<String, OpponentTracker> TRACKER_MEMORY = new HashMap<>();

    // Preflop Strength Categories
    private static final int HAND_MONSTER = 5;
    private static final int HAND_PREMIUM = 4;
    private static final int HAND_STRONG  = 3;
    private static final int HAND_PLAYABLE = 2;
    private static final int HAND_STEAL   = 1;
    private static final int HAND_TRASH   = 0;

    public enum Position { EARLY, MIDDLE, LATE, BLINDS, UNKNOWN }
    public enum Profile { NIT, STATION, TAG, LAG }

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

    // --- INNER CLASS: OPPONENT TRACKER ---
    public static class OpponentTracker {
        private int handsPlayed = 0;
        private int voluntarilyPutInPot = 0; 
        private int preflopRaises = 0;       

        public void recordHandPlayed() { handsPlayed++; }
        public void recordVPIP() { voluntarilyPutInPot++; }
        public void recordPFR() { preflopRaises++; }

        public double getVPIP() { return handsPlayed == 0 ? 0 : (double) voluntarilyPutInPot / handsPlayed; }
        public double getPFR() { return handsPlayed == 0 ? 0 : (double) preflopRaises / handsPlayed; }

        public boolean isStation() { return handsPlayed > 10 && getVPIP() > 0.45 && getPFR() < 0.10; }
        public boolean isNit() { return handsPlayed > 10 && getVPIP() < 0.15; }
        public boolean isManiac() { return handsPlayed > 10 && getVPIP() > 0.40 && getPFR() > 0.30; }
    }
    // --------------------------------------

    public Bot(RemotePlayer player) {
        this.cpuPlayer = player;
        assignBaseProfile();
    }
    
    private void assignBaseProfile() {
        int roll = Helpers.CSPRNG_GENERATOR.nextInt(100);
        if (roll < 20) baseProfile = Profile.NIT;            // 20% Rock
        else if (roll < 40) baseProfile = Profile.STATION;   // 20% Calling Station
        else if (roll < 80) baseProfile = Profile.TAG;       // 40% Solid Shark
        else baseProfile = Profile.LAG;                      // 20% Maniac
        currentProfile = baseProfile;
    }

    private void adjustProfileElasticity() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float stack = cpuPlayer.getStack();
        float blindsCost = dealer.getCiega_grande() + dealer.getCiega_pequeña();
        float mRatio = stack / (blindsCost > 0 ? blindsCost : 1);

        if (mRatio < 12.0f && (baseProfile == Profile.LAG || baseProfile == Profile.STATION)) {
            currentProfile = Profile.TAG; // Shift to tighter push/fold strategy
        } else if (mRatio > 60.0f && baseProfile == Profile.NIT) {
            currentProfile = Profile.TAG; // Deep stacks allow nits to loosen up
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
                // 3Bet sizing based on profile
                targetBet = Helpers.floatClean(minRaise * (currentProfile == Profile.LAG ? 3.5f : 3.0f)); 
            } else {
                // Open raise + isolate limpers
                int limpers = dealer.getLimpersCount();
                targetBet = Helpers.floatClean((2.5f + (limpers * 1.5f)) * bb);
            }
        } else {
            int textureScore = calculateBoardTexture();
            
            // Texture-based sizing
            if (textureScore >= 3) {
                targetBet = pot * 0.75f; // Protect heavy draws
            } else if (textureScore == 2) {
                targetBet = pot * 0.55f; // Standard value bet
            } else {
                targetBet = pot * 0.33f; // Dry board C-Bet
            }
            
            // Polarized River Overbets for aggressive profiles
            if (dealer.getStreet() == Crupier.RIVER && (currentProfile == Profile.LAG || currentProfile == Profile.TAG)) {
                if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 15) {
                    targetBet = pot * 1.5f; 
                }
            }
            
            // Inject Gaussian Noise (10% chance) to avoid exact sizing tells
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

        if (street == Crupier.PREFLOP) {
            int decision = calculatePreflopAction(activePlayers);
            if (decision == Player.BET && currentProfile != Profile.STATION) {
                cBetInitiative = true; 
            }
            return decision;
        }

        // Postflop EV Engine
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
        boolean potCommitted = (callCost > 0 && callCost <= remainingStack * 0.30f && effectiveStrength > 0.65);

        // Modify perceived win probability based on board evolution and profile
        double winProb = effectiveStrength;
        if (scareCardDetected) {
            if (currentProfile == Profile.NIT || currentProfile == Profile.STATION) {
                winProb -= 0.20; // Passive players fear the board
            } else {
                winProb += 0.05; // Aggressive players see bluff opportunities
            }
        }

        double evCall = (winProb * pot) - ((1.0 - winProb) * callCost);
        
        // Fold Equity Calculation using Tracker Memory
        OpponentTracker targetStats = getPrimaryOpponentStats();
        double foldEquity = 0.20; 
        
        if (targetStats != null) {
            if (targetStats.isStation()) foldEquity -= 0.15; 
            if (targetStats.isNit()) foldEquity += 0.20;     
            if (targetStats.isManiac()) foldEquity -= 0.10;  
        }
        
        if (betCount == 0) foldEquity += 0.15; 
        if (calculateBoardTexture() >= 3) foldEquity -= 0.10; 
        foldEquity = Math.max(0, foldEquity / (activePlayers - 1)); 
        
        double raiseAmount = getBetSize();
        double raiseCost = raiseAmount - cpuPlayer.getBet();
        double evRaise = (foldEquity * pot) + ((1.0 - foldEquity) * ((winProb * (pot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        // Decision Tree Implementation
        int decision = Player.FOLD;

        if (betCount == 0) {
            if (slowPlay && effectiveStrength > 0.90 && street < Crupier.RIVER) {
                decision = Player.CHECK;
            } else if (street == Crupier.RIVER) {
                if (effectiveStrength > 0.85) {
                    decision = Player.BET; 
                } else if (effectiveStrength < 0.30 && (currentProfile == Profile.LAG || foldEquity > 0.35)) {
                    decision = Player.BET; // Pure Bluff
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

            if (potCommitted) {
                decision = Player.CHECK; 
                callCount++;
            } else if (evRaise > evCall && evRaise > 0 && effectiveStrength > 0.80 && currentProfile != Profile.STATION) {
                decision = Player.BET; 
                callCount++;
            } else if (evCall > 0 || ppot > 1.5 * potOdds()) {
                decision = Player.CHECK; 
                callCount++;
            } else {
                decision = Player.FOLD;
            }
        }
        return decision;
    }

    private int calculatePreflopAction(int activePlayers) {
        Position pos = determinePosition();
        int handGroup = getHandGroup();
        int betCount = GameFrame.getInstance().getCrupier().getConta_bet();
        
        // Handle All-Ins or Massive raises
        if (betCount > 0 && cpuPlayer.getStack() > 0 && ((GameFrame.getInstance().getCrupier().getApuesta_actual() - cpuPlayer.getBet()) > (cpuPlayer.getStack() * 0.20f))) {
            if (handGroup >= HAND_PREMIUM || (handGroup == HAND_STRONG && currentProfile == Profile.LAG)) {
                return Player.CHECK; 
            }
            return Player.FOLD;
        }

        // Standard facing raise
        if (betCount > 0) {
            if (handGroup >= HAND_PREMIUM) return (betCount < Bot.MAX_BET_COUNT && !slowPlay) ? Player.BET : Player.CHECK;
            if (handGroup == HAND_STRONG) return Player.CHECK;
            if (handGroup == HAND_PLAYABLE && currentProfile != Profile.NIT) return Player.CHECK; 
            return Player.FOLD;
        }

        // Open Action Mapping
        switch (pos) {
            case EARLY:
                if (handGroup >= HAND_STRONG) return Player.BET;
                if (handGroup == HAND_PLAYABLE && (currentProfile == Profile.STATION || currentProfile == Profile.LAG)) return Player.CHECK; 
                return Player.FOLD;
            case MIDDLE:
                if (handGroup >= HAND_PLAYABLE) return Player.BET;
                if (handGroup == HAND_STEAL && currentProfile == Profile.LAG) return Player.BET;
                return Player.FOLD;
            case LATE:
                if (handGroup >= HAND_STEAL && currentProfile != Profile.NIT) return Player.BET;
                if (handGroup >= HAND_PLAYABLE) return Player.BET;
                return Player.FOLD;
            case BLINDS:
                if (handGroup >= HAND_STRONG) return Player.BET;
                if (handGroup >= HAND_PLAYABLE && currentProfile != Profile.NIT) return Player.CHECK;
                return Player.CHECK;
            default:
                return Player.FOLD;
        }
    }

    /**
     * Comprehensive Board Texture Scoring
     * @return 0 (Dry) to 5 (Extremely Wet/Coordinated)
     */
    private int calculateBoardTexture() {
        if (BOT_COMMUNITY_CARDS.size() < 3) return 0;
        int score = 0;
        int[] suits = new int[4];
        int maxRank = 0; int minRank = 14;
        boolean pairOnBoard = false;
        int[] ranks = new int[15];

        for (int i = 1; i <= BOT_COMMUNITY_CARDS.size(); i++) {
            org.alberta.poker.Card c = BOT_COMMUNITY_CARDS.getCard(i);
            suits[c.getSuit()]++;
            ranks[c.getRank()]++;
            if (ranks[c.getRank()] == 2) pairOnBoard = true;
            if (c.getRank() > maxRank) maxRank = c.getRank();
            if (c.getRank() < minRank) minRank = c.getRank();
        }

        for (int s : suits) {
            if (s == 2) score += 1; // Flush draw present
            if (s >= 3) score += 3; // Flush heavily present/made
        }

        int gap = maxRank - minRank;
        if (gap <= 4) score += 2; // Open-Ended Straight Draw (OESD) or made straight
        else if (gap <= 5) score += 1; // Gutshot possible

        if (pairOnBoard) score += 1; // Full house or Quads possible

        return Math.min(score, 5);
    }

    private Position determinePosition() {
        String myNick = cpuPlayer.getNickname();
        Crupier crupier = GameFrame.getInstance().getCrupier();
        if (myNick.equals(crupier.getUtg_nick())) return Position.EARLY;
        if (myNick.equals(crupier.getDealer_nick())) return Position.LATE;
        if (myNick.equals(crupier.getSb_nick()) || myNick.equals(crupier.getBb_nick())) return Position.BLINDS;
        return Position.MIDDLE;
    }

    private int getHandGroup() {
        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);

        if (isPair) {
            if (high >= 10) return HAND_MONSTER; 
            if (high >= 8) return HAND_PREMIUM;  
            if (high >= 5) return HAND_STRONG;   
            return HAND_PLAYABLE;                
        }
        if (high == 12) { // Ace
            if (low >= 10) return HAND_MONSTER; 
            if (low >= 9) return suited ? HAND_PREMIUM : HAND_STRONG; 
            if (low >= 7) return HAND_PLAYABLE; 
            if (suited) return HAND_PLAYABLE;   
            return HAND_STEAL;                  
        }
        if (high == 11) { // King
            if (low >= 10) return suited ? HAND_PREMIUM : HAND_STRONG; 
            if (low >= 9) return HAND_PLAYABLE; 
            if (suited && low >= 7) return HAND_PLAYABLE; 
        }
        if (suited && (high - low <= 3) && high >= 8) return HAND_PLAYABLE; // Expanded suited connectors/gappers
        if (high >= 9 && low >= 8) return HAND_STEAL; 

        return HAND_TRASH;
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
    public static int coronaCardSuit2LokiCardSuit(Card carta) { return Bot.SUITS.indexOf(carta.getPalo()); }
    
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