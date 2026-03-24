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

/**
 * FULLY EXPERIMENTAL. B-A-S-E-D on the mythical Alberta's Loki Bot
 * * ULTIMATE GTO/EXPLOITATIVE ENGINE:
 * - Personality Profiles (NIT, STATION, TAG, LAG)
 * - Positional Preflop Charts
 * - EV-Based Post-Flop Math (Fold Equity scaling with opponents)
 * - Scare Card Delta Analysis (Board memory)
 * - Gaussian Noise Sizing (Unpredictable bet sizing)
 * - Pot Commitment & Pot Control logic
 *
 * @author tonikelope
 */
public class Bot {

    public static final String SUITS = "TDCP";
    public static final int MAX_CONTA_BET = 2;
    public static final int BOT_THINK_TIME = 1500;
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    // Hand strength categories for Preflop charts
    private static final int HAND_PREMIUM = 4;
    private static final int HAND_STRONG  = 3;
    private static final int HAND_PLAYABLE = 2;
    private static final int HAND_STEAL   = 1;
    private static final int HAND_TRASH   = 0;

    public enum Position { EARLY, MIDDLE, LATE, BLINDS, UNKNOWN }
    
    // Personality Profiles for a dynamic ecosystem
    public enum Profile { NIT, STATION, TAG, LAG }

    private volatile RemotePlayer cpu_player = null;
    private volatile Profile profile;
    
    private volatile boolean semi_bluff = false;
    private volatile org.alberta.poker.Card hole_card1 = null;
    private volatile org.alberta.poker.Card hole_card2 = null;
    private volatile int conta_call = 0;
    private volatile boolean slow_play = false;
    private volatile boolean cbet = false;
    
    // Delta Analysis memory
    private volatile double prev_strength = -1.0;
    private volatile int prev_street = -1;
    private volatile boolean scare_card_panic = false;

    public Bot(RemotePlayer player) {
        cpu_player = player;
        assignRandomProfile();
    }
    
    private void assignRandomProfile() {
        int roll = Helpers.CSPRNG_GENERATOR.nextInt(100);
        if (roll < 20) profile = Profile.NIT;           // 20% Rock
        else if (roll < 40) profile = Profile.STATION;  // 20% Calling Station
        else if (roll < 80) profile = Profile.TAG;      // 40% Solid Shark
        else profile = Profile.LAG;                     // 20% Maniac
    }

    // CALLED FROM THE DEALER ONCE CARDS ARE DEALT TO THE PLAYER
    public void resetBot() {
        hole_card1 = Bot.coronaCard2LokiCard(cpu_player.getHoleCard1());
        hole_card2 = Bot.coronaCard2LokiCard(cpu_player.getHoleCard2());

        semi_bluff = false;
        slow_play = Helpers.CSPRNG_GENERATOR.nextBoolean();
        conta_call = 0;
        cbet = false;
        
        prev_strength = -1.0;
        prev_street = Crupier.PREFLOP;
        scare_card_panic = false;
    }

    /**
     * Determines the optimal bet size based on Pot Size, Board Texture, and Gaussian Noise.
     */
    public float getBetSize() {
        Crupier crupier = GameFrame.getInstance().getCrupier();
        float pot = crupier.getBote_total();
        float current_bet = crupier.getApuesta_actual();
        float min_raise = Helpers.float1DSecureCompare(0f, crupier.getUltimo_raise()) < 0 ? crupier.getUltimo_raise() : crupier.getCiega_grande();
        float bb = crupier.getCiega_grande();
        
        float target_bet;

        if (crupier.getStreet() == Crupier.PREFLOP) {
            if (crupier.getConta_bet() > 0) {
                // 3-Bet / 4-Bet sizing (roughly 3x the last raise)
                target_bet = Helpers.floatClean(min_raise * 3f);
            } else {
                // Open raise: 2.5x to 3.5x + 1bb per limper
                target_bet = Helpers.floatClean((2.5f + (Helpers.CSPRNG_GENERATOR.nextFloat() * 1.5f) + crupier.getLimpersCount()) * bb);
            }
        } else {
            // Postflop sizing based on board texture
            if (isWetBoard()) {
                // Protect hand on wet boards: 66% to 75% of pot
                target_bet = Helpers.floatClean(pot * (0.66f + (Helpers.CSPRNG_GENERATOR.nextFloat() * 0.1f)));
            } else {
                // Cheaper continuation on dry boards: 33% to 50% of pot
                target_bet = Helpers.floatClean(pot * (0.33f + (Helpers.CSPRNG_GENERATOR.nextFloat() * 0.17f)));
            }
            
            // Inject Gaussian Noise (10% chance) to avoid sizing tells
            if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 10) {
                if (Helpers.CSPRNG_GENERATOR.nextBoolean()) {
                    target_bet = pot * 1.25f; // Overbet
                } else {
                    target_bet = pot * 0.20f; // Blocker bet
                }
            }
            
            // Round to nearest small blind
            target_bet = (float) (Math.ceil(target_bet / GameFrame.CIEGA_PEQUEÑA) * GameFrame.CIEGA_PEQUEÑA);
        }

        // Ensure we meet the legal minimum raise rules
        if (Helpers.float1DSecureCompare(current_bet, 0f) == 0 || (crupier.getStreet() == Crupier.PREFLOP && Helpers.float1DSecureCompare(current_bet, bb) == 0)) {
            return Math.max(bb, target_bet);
        } else {
            return Math.max(current_bet + min_raise, current_bet + target_bet);
        }
    }

    public int calculateBotDecision(int opponents) {
        Crupier crupier = GameFrame.getInstance().getCrupier();
        int fase = crupier.getStreet();
        int activos = crupier.getJugadoresActivos();

        // --------------------------------------------------------
        // PREFLOP PHASE
        // --------------------------------------------------------
        if (fase == Crupier.PREFLOP) {
            int dec = calculatePreflopAction(activos);
            // High chance to C-Bet the flop if we are the preflop aggressor
            if (dec == Player.BET && profile != Profile.STATION) {
                cbet = Helpers.CSPRNG_GENERATOR.nextInt(100) < (profile == Profile.LAG ? 85 : 65); 
            }
            return dec;
        }

        // --------------------------------------------------------
        // POSTFLOP PHASE: Delta Analysis & EV Engine
        // --------------------------------------------------------
        double strength = HANDEVALUATOR.handRank(hole_card1, hole_card2, Bot.BOT_COMMUNITY_CARDS, opponents);
        double ppot = HANDPOTENTIAL.ppot_raw(hole_card1, hole_card2, Bot.BOT_COMMUNITY_CARDS, true);
        double npot = HANDPOTENTIAL.getLastNPot();
        
        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;
        
        // Scare Card Delta Analysis
        if (fase != prev_street) {
            if (prev_strength != -1.0) {
                double delta = effectiveStrength - prev_strength;
                // If our hand strength drops massively, the board got scary
                if (delta < -0.15) {
                    scare_card_panic = true;
                } else {
                    scare_card_panic = false;
                }
            }
            prev_strength = effectiveStrength;
            prev_street = fase;
        }

        double pot = crupier.getBote_total();
        double callCost = crupier.getApuesta_actual() - cpu_player.getBet();
        int contaBet = crupier.getConta_bet();

        // Pot Commitment Rule
        float remainingStack = cpu_player.getStack();
        boolean potCommitted = (callCost > 0 && callCost <= remainingStack * 0.25f && effectiveStrength > 0.60);

        // Expected Value (EV) Math Core
        double evFold = 0;
        double winProb = effectiveStrength;
        
        // Personality tweaks to perceived win probability
        if (profile == Profile.NIT) winProb -= 0.05; // Pessimistic
        if (profile == Profile.STATION) winProb += 0.10; // Over-optimistic
        if (scare_card_panic) winProb -= 0.20; // Panic penalty

        double evCall = (winProb * pot) - ((1.0 - winProb) * callCost);
        
        // Fold Equity estimates (Scales down with multiple opponents)
        double baseFE = 0.15; 
        if (contaBet == 0) baseFE += 0.20; 
        if (isWetBoard()) baseFE -= 0.10;  
        if (cbet && fase == Crupier.FLOP) baseFE += 0.15; 
        if (profile == Profile.LAG) baseFE += 0.15; // LAGs overvalue Fold Equity
        
        double foldEquity = Math.max(0, baseFE / (activos - 1)); // Multi-way fix
        
        double raiseAmount = getBetSize();
        double raiseCost = raiseAmount - cpu_player.getBet();
        double evRaise = (foldEquity * pot) + ((1.0 - foldEquity) * ((winProb * (pot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        // --------------------------------------------------------
        // DECISION TREE
        // --------------------------------------------------------
        int decision = Player.FOLD;

        if (contaBet == 0) {
            // Nobody has bet. Do we bet or check?
            if (slow_play && effectiveStrength > 0.90) {
                decision = Player.CHECK;
            } else if (fase == Crupier.RIVER) {
                // Polarized River Strategy
                if (effectiveStrength > 0.85) {
                    decision = Player.BET; // Value bet
                } else if (effectiveStrength < 0.30 && (profile == Profile.LAG || foldEquity > 0.30) && Helpers.CSPRNG_GENERATOR.nextInt(100) < 25) {
                    decision = Player.BET; // Pure Bluff
                } else {
                    decision = Player.CHECK; // Bluff-catcher / Give up
                }
            } else if (effectiveStrength > 0.60 && effectiveStrength <= 0.80 && profile != Profile.LAG) {
                // Pot Control for medium hands
                decision = Player.CHECK;
            } else if (evRaise > 0 && evRaise > evCall) {
                decision = Player.BET;
                conta_call++;
            } else if (cbet && fase == Crupier.FLOP && evRaise > -0.5 && !scare_card_panic) {
                decision = Player.BET;
                cbet = false;
            } else {
                decision = Player.CHECK;
            }
        } else {
            // Facing a bet.
            cbet = false; 

            // Protect against endless raising loops
            if (contaBet >= Bot.MAX_CONTA_BET) {
                evRaise = -9999; 
            }

            if (potCommitted) {
                decision = Player.CHECK; // Check acts as Call when facing a bet in this engine
                conta_call++;
            } else if (evRaise > evCall && evRaise > evFold && effectiveStrength > 0.80 && profile != Profile.STATION) {
                decision = Player.BET; // Re-raise
                conta_call++;
            } else if (evCall > evFold || ppot > 1.5 * potOdds()) {
                decision = Player.CHECK; // Call
                conta_call++;
            } else {
                decision = Player.FOLD;
            }
        }

        return decision;
    }

    /**
     * Determines the Preflop action based on hand strength, table position, and Profile.
     */
    private int calculatePreflopAction(int activePlayers) {
        Position pos = determinePosition();
        int handGroup = getHandGroup();
        int contaBet = GameFrame.getInstance().getCrupier().getConta_bet();
        
        // Facing an All-In or massive raise (> 15% of stack)
        if (contaBet > 0 && cpu_player.getStack() > 0 && ((GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) > (cpu_player.getStack() * 0.15f))) {
            if (handGroup == HAND_PREMIUM || (handGroup == HAND_STRONG && profile == Profile.LAG)) {
                return Player.CHECK; // Call
            }
            return Player.FOLD;
        }

        // Facing a standard raise
        if (contaBet > 0) {
            if (handGroup == HAND_PREMIUM) {
                return (contaBet < Bot.MAX_CONTA_BET && !this.slow_play && profile != Profile.STATION) ? Player.BET : Player.CHECK;
            }
            if (handGroup == HAND_STRONG) return Player.CHECK;
            
            // Defend playable hands if not a Nit
            if (handGroup == HAND_PLAYABLE && profile != Profile.NIT) return Player.CHECK; 
            
            return Player.FOLD;
        }

        // Opening the action
        switch (pos) {
            case EARLY:
                if (handGroup >= HAND_STRONG) return Player.BET;
                if (handGroup == HAND_PLAYABLE && (profile == Profile.STATION || profile == Profile.LAG)) return Player.CHECK; 
                return Player.FOLD;

            case MIDDLE:
                if (handGroup >= HAND_PLAYABLE) return Player.BET;
                if (handGroup == HAND_STEAL && profile == Profile.LAG) return Player.BET;
                return Player.FOLD;

            case LATE:
                if (handGroup >= HAND_STEAL && profile != Profile.NIT) return Player.BET;
                if (handGroup >= HAND_PLAYABLE) return Player.BET;
                return Player.FOLD;

            case BLINDS:
                if (handGroup >= HAND_STRONG) return Player.BET;
                if (handGroup >= HAND_PLAYABLE && profile == Profile.STATION) return Player.CHECK;
                return Player.CHECK;
                
            default:
                return Player.FOLD;
        }
    }

    /**
     * Analyzes community cards to determine if the board is coordinated/wet.
     */
    private boolean isWetBoard() {
        if (BOT_COMMUNITY_CARDS.size() < 3) return false;

        int[] suits = new int[4];
        int maxRank = 0;
        int minRank = 14;

        for (int i = 1; i <= BOT_COMMUNITY_CARDS.size(); i++) {
            org.alberta.poker.Card c = BOT_COMMUNITY_CARDS.getCard(i);
            suits[c.getSuit()]++;
            int rank = c.getRank();
            if (rank > maxRank) maxRank = rank;
            if (rank < minRank) minRank = rank;
        }

        for (int s : suits) {
            if (s >= 2) return true; // Flush draw
        }

        if (maxRank - minRank <= 4) return true; // Straight draw

        return false;
    }

    private Position determinePosition() {
        String myNick = cpu_player.getNickname();
        Crupier crupier = GameFrame.getInstance().getCrupier();
        
        if (myNick.equals(crupier.getUtg_nick())) return Position.EARLY;
        if (myNick.equals(crupier.getDealer_nick())) return Position.LATE;
        if (myNick.equals(crupier.getSb_nick()) || myNick.equals(crupier.getBb_nick())) return Position.BLINDS;
        
        return Position.MIDDLE;
    }

    private int getHandGroup() {
        int v1 = hole_card1.getRank();
        int v2 = hole_card2.getRank();
        boolean suited = hole_card1.getSuit() == hole_card2.getSuit();
        
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);

        if (isPair) {
            if (high >= 9) return HAND_PREMIUM; 
            if (high >= 6) return HAND_STRONG;  
            return HAND_PLAYABLE;               
        }

        if (high == 12) { // Ace
            if (low >= 10) return HAND_PREMIUM; 
            if (low >= 9 && suited) return HAND_STRONG; 
            if (low >= 8) return HAND_PLAYABLE; 
            if (suited) return HAND_PLAYABLE;   
            return HAND_STEAL;                  
        }

        if (high == 11) { // King
            if (low >= 10 && suited) return HAND_STRONG; 
            if (low >= 10) return HAND_PLAYABLE; 
            if (suited && low >= 8) return HAND_PLAYABLE; 
        }

        if (suited && (high - low <= 2) && high >= 7) {
            return HAND_PLAYABLE; 
        }

        if (high >= 9 && low >= 8) return HAND_STEAL; 

        return HAND_TRASH;
    }

    private float potOdds() {
        return (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) / (GameFrame.getInstance().getCrupier().getBote_total() + (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()));
    }

    public static int coronaCardSuit2LokiCardSuit(Card carta) {
        return Bot.SUITS.indexOf(carta.getPalo());
    }

    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(int carta) {
        int v = (carta - 1) % 13;
        int corona_valor = (v == 0 ? 14 : v + 1);
        String corona_palo = Card.PALOS[(int) ((float) (carta - 1) / 13)];
        return new org.alberta.poker.Card(corona_valor - 2, Bot.SUITS.indexOf(corona_palo));
    }

    public static org.alberta.poker.Card coronaCard2LokiCard(Card carta) {
        return new org.alberta.poker.Card(carta.getValorNumerico() - 2, Bot.coronaCardSuit2LokiCardSuit(carta));
    }
}