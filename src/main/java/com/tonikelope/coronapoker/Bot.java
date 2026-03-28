/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THE "PERFECT" REALISTIC ENGINE (V4 - Total Redesign)
 *
 * Double-Axis System: Skill (Recreational, Regular, Shark) + Profile (Tag, Lag,
 * Station, Nit) with Difficulty scaling (Easy, Medium, Hard).
 *
 * V4 Features: - Full board texture analysis (suits, connectivity, pairs, high
 * cards) - Check-raise planning for skilled bots - Floating capability for
 * positional play - Multi-street planning (bet-bet-bet, bet-check-bet, etc.) -
 * Adjusted c-bet frequency by opponents and board texture - Improved bluffing
 * with busted draws and aggression lines - Tilt simulation for recreationals -
 * Squeeze play detection for aggressive bots - Dynamic profile elasticity based
 * on stack depth - Difficulty parameter for overall bot strength scaling -
 * Corrected EV formulas and pot commitment logic
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

    // Global difficulty setting (default MEDIUM)
    public static volatile Difficulty DIFFICULTY = Difficulty.MEDIUM;

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public enum Position {
        EARLY, MIDDLE, LATE, BLINDS, UNKNOWN
    }

    public enum Profile {
        NIT, STATION, TAG, LAG
    }

    public enum Skill {
        RECREATIONAL, REGULAR, SHARK
    }

    // --- Multi-street plan constants ---
    private static final int PLAN_NONE = 0;
    private static final int PLAN_BET_BET_BET = 1;       // Value / Triple barrel bluff
    private static final int PLAN_BET_CHECK_BET = 2;      // Pot control with medium hand
    private static final int PLAN_CHECK_CALL = 3;           // Trapping / Calling down

    // --- Board texture result ---
    private static class BoardTexture {

        int flushDanger;      // 0=rainbow, 1=two-tone, 2=two-tone strong, 3=monotone
        int straightDanger;   // 0=disconnected, 1=some connected, 2=very connected, 3=three-straight
        boolean isPaired;
        boolean hasHighCards;  // >=2 cards Q+
        int totalScore;

        BoardTexture() {
            flushDanger = 0;
            straightDanger = 0;
            isPaired = false;
            hasHighCards = false;
            totalScore = 0;
        }
    }

    // --- OPPONENT TRACKER ---
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
                return postFlopBetsAndRaises > 0 ? 3.0 : 1.0;
            }
            return Math.min(3.0, (double) postFlopBetsAndRaises / postFlopCalls);
        }

        public boolean isStation() {
            return handsPlayed > 10 && getVPIP() > 0.45 && getPFR() < 0.10;
        }

        public boolean isNit() {
            return handsPlayed > 10 && getVPIP() < 0.15;
        }

        public boolean isManiac() {
            return handsPlayed > 10 && getVPIP() > 0.35 && getAF() > 2.0;
        }

        public boolean hasEnoughData() {
            return handsPlayed > 10;
        }
    }

    private volatile RemotePlayer cpuPlayer = null;
    private volatile Profile baseProfile;
    private volatile Profile currentProfile;
    private volatile Skill skillLevel;

    private volatile org.alberta.poker.Card holeCard1 = null;
    private volatile org.alberta.poker.Card holeCard2 = null;
    private volatile boolean slowPlay = false;
    private volatile boolean cBetInitiative = false;

    private volatile double previousStrength = -1.0;
    private volatile int previousStreet = -1;
    private volatile boolean scareCardDetected = false;

    // V4: Advanced state
    private volatile boolean planCheckRaise = false;
    private volatile boolean floatPlay = false;
    private volatile int streetPlan = PLAN_NONE;
    private volatile int streetPlanStartStreet = -1;
    private volatile int consecutiveLosses = 0;
    private volatile boolean onTilt = false;
    private volatile double previousPpot = 0.0;
    private volatile boolean aggressiveLine = false; // Have we bet on previous streets?

    public Bot(RemotePlayer player) {
        this.cpuPlayer = player;
        assignPersonality();
    }

    /**
     * Logically distributes Skill and Profile. Difficulty shifts the
     * distribution: EASY = more recreationals, HARD = more sharks/regs.
     */
    private void assignPersonality() {
        int skillRoll = Helpers.CSPRNG_GENERATOR.nextInt(100);
        int styleRoll = Helpers.CSPRNG_GENERATOR.nextInt(100);

        // Difficulty-adjusted skill distribution
        int recThreshold;
        int regThreshold;

        switch (DIFFICULTY) {
            case EASY:
                recThreshold = 50;  // 50% Recreational
                regThreshold = 90;  // 40% Regular, 10% Shark
                break;
            case HARD:
                recThreshold = 15;  // 15% Recreational
                regThreshold = 75;  // 60% Regular, 25% Shark
                break;
            default: // MEDIUM
                recThreshold = 30;  // 30% Recreational
                regThreshold = 85;  // 55% Regular, 15% Shark
                break;
        }

        if (skillRoll < recThreshold) {
            skillLevel = Skill.RECREATIONAL;
        } else if (skillRoll < regThreshold) {
            skillLevel = Skill.REGULAR;
        } else {
            skillLevel = Skill.SHARK;
        }

        // Profile distribution based on Skill (Logical and varied)
        if (skillLevel == Skill.RECREATIONAL) {
            if (styleRoll < 50) {
                baseProfile = Profile.STATION;     // Mostly passive callers
            } else if (styleRoll < 75) {
                baseProfile = Profile.LAG;          // Some crazy maniacs
            } else if (styleRoll < 90) {
                baseProfile = Profile.TAG;          // "I think I'm good" recreational
            } else {
                baseProfile = Profile.NIT;          // Few scared rocks
            }
        } else if (skillLevel == Skill.REGULAR) {
            if (styleRoll < 60) {
                baseProfile = Profile.TAG;          // Mostly standard solid
            } else if (styleRoll < 80) {
                baseProfile = Profile.LAG;          // Aggressive regs (more common now)
            } else if (styleRoll < 95) {
                baseProfile = Profile.NIT;          // Some overly tight
            } else {
                baseProfile = Profile.STATION;      // Rare passive reg
            }
        } else { // SHARK
            if (styleRoll < 55) {
                baseProfile = Profile.TAG;          // Optimal aggressive
            } else {
                baseProfile = Profile.LAG;          // Hyper-aggressive optimal
            }
        }

        currentProfile = baseProfile;
    }

    /**
     * Adapts the profile dynamically based on stack depth and game conditions.
     */
    private void adjustProfileElasticity() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float stack = cpuPlayer.getStack();
        float blindsCost = dealer.getCiega_grande() + dealer.getCiega_pequeña();
        float mRatio = stack / (blindsCost > 0 ? blindsCost : 1);

        if (skillLevel == Skill.RECREATIONAL) {
            // Recreationals don't adapt, but tilt affects them
            currentProfile = onTilt ? Profile.LAG : baseProfile;
            return;
        }

        // Short stack: Everyone becomes push-or-fold TAG
        if (mRatio < 8.0f) {
            currentProfile = Profile.TAG;
            return;
        }

        // Short-ish stack: LAGs tighten up
        if (mRatio < 15.0f && baseProfile == Profile.LAG) {
            currentProfile = Profile.TAG;
            return;
        }

        // Deep stack: NITs loosen slightly, LAGs get more aggressive
        if (mRatio > 50.0f) {
            if (baseProfile == Profile.NIT) {
                currentProfile = Profile.TAG;
                return;
            }
            // Deep stack LAGs stay LAG (they thrive here)
        }

        currentProfile = baseProfile;
    }

    public void resetBot() {
        holeCard1 = Bot.coronaCard2LokiCard(cpuPlayer.getHoleCard1());
        holeCard2 = Bot.coronaCard2LokiCard(cpuPlayer.getHoleCard2());

        adjustProfileElasticity();

        // Slowplay decision: Sharks do it more, TAGs occasionally, others rarely
        if (skillLevel == Skill.RECREATIONAL) {
            slowPlay = false;
        } else if (skillLevel == Skill.SHARK) {
            slowPlay = Helpers.CSPRNG_GENERATOR.nextInt(100) < 20;
        } else {
            slowPlay = Helpers.CSPRNG_GENERATOR.nextInt(100) < (currentProfile == Profile.TAG ? 12 : 4);
        }

        // Check-raise planning: Only skilled bots, mostly in position or from blinds
        planCheckRaise = false;
        if (skillLevel != Skill.RECREATIONAL && currentProfile != Profile.STATION) {
            Position pos = determinePosition();
            int crChance = (skillLevel == Skill.SHARK) ? 15 : 8;
            if (pos == Position.BLINDS || pos == Position.EARLY) {
                planCheckRaise = Helpers.CSPRNG_GENERATOR.nextInt(100) < crChance;
            }
        }

        floatPlay = false;
        cBetInitiative = false;
        previousStrength = -1.0;
        previousStreet = Crupier.PREFLOP;
        scareCardDetected = false;
        streetPlan = PLAN_NONE;
        streetPlanStartStreet = -1;
        previousPpot = 0.0;
        aggressiveLine = false;

        // Tilt for recreationals: After 2+ consecutive losses
        if (skillLevel == Skill.RECREATIONAL && consecutiveLosses >= 2) {
            onTilt = Helpers.CSPRNG_GENERATOR.nextInt(100) < (30 + consecutiveLosses * 10);
        } else {
            onTilt = false;
        }
    }

    /**
     * Records hand result for tilt tracking.
     */
    public void recordHandResult(boolean won) {
        if (won) {
            consecutiveLosses = 0;
            onTilt = false;
        } else {
            consecutiveLosses++;
        }
    }

    // =====================================================================
    // BET SIZING
    // =====================================================================
    public float getBetSize() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float pot = dealer.getBote_total();
        float currentBet = dealer.getApuesta_actual();
        float minRaise = Helpers.float1DSecureCompare(0f, dealer.getUltimo_raise()) < 0 ? dealer.getUltimo_raise() : dealer.getCiega_grande();
        float bb = dealer.getCiega_grande();
        float targetBet;

        if (dealer.getStreet() == Crupier.PREFLOP) {
            if (dealer.getConta_bet() > 0) {
                // 3-bet / squeeze sizing
                targetBet = Math.min(minRaise * 3.0f, (pot > 0 ? pot * 1.5f : minRaise * 4.0f));
            } else {
                int limpers = dealer.getLimpersCount();
                targetBet = (2.5f + (limpers * 1.0f)) * bb;
            }
        } else {
            BoardTexture texture = calculateFullBoardTexture();

            if (skillLevel == Skill.RECREATIONAL) {
                // Recreationals use simple sizing with some variance
                float rec_pct = 0.45f + (Helpers.CSPRNG_GENERATOR.nextFloat() * 0.20f);
                targetBet = pot * rec_pct;
            } else {
                // Skilled bots size based on board texture
                if (texture.totalScore >= 4) {
                    targetBet = pot * 0.70f;    // Wet board: protect
                } else if (texture.totalScore >= 2) {
                    targetBet = pot * 0.55f;    // Medium board
                } else {
                    targetBet = pot * 0.33f;    // Dry board: small sizing
                }

                // Adjust by profile
                if (currentProfile == Profile.LAG) {
                    targetBet *= 1.10f; // LAGs bet bigger
                } else if (currentProfile == Profile.NIT) {
                    targetBet *= 0.90f; // NITs bet smaller
                }
            }

            // Sharks: Polarized overbets on river with very strong or very weak hands
            if (skillLevel == Skill.SHARK && dealer.getStreet() == Crupier.RIVER
                    && Helpers.CSPRNG_GENERATOR.nextInt(100) < 18) {
                targetBet = pot * 1.25f;
            }
        }

        // Noise: 30% chance of ±15% variation
        if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 30) {
            float noiseFactor = 1.0f + (Helpers.CSPRNG_GENERATOR.nextFloat() * 0.30f - 0.15f);
            targetBet *= noiseFactor;
        }

        targetBet = (float) (Math.ceil(Helpers.floatClean(targetBet) / GameFrame.CIEGA_PEQUEÑA) * GameFrame.CIEGA_PEQUEÑA);

        if (Helpers.float1DSecureCompare(currentBet, 0f) == 0 || (dealer.getStreet() == Crupier.PREFLOP && Helpers.float1DSecureCompare(currentBet, bb) == 0)) {
            return Math.max(bb, targetBet);
        } else {
            return Math.max(currentBet + minRaise, currentBet + targetBet);
        }
    }

    // =====================================================================
    // MAIN DECISION ENGINE
    // =====================================================================
    public int calculateBotDecision(int opponentsCount) {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        int street = dealer.getStreet();
        int activePlayers = dealer.getJugadoresActivos();
        int betCount = dealer.getConta_bet();

        if (street == Crupier.PREFLOP) {
            int decision = calculatePreflopAction(betCount, activePlayers);
            if (decision == Player.BET && currentProfile != Profile.STATION) {
                cBetInitiative = true;
            }
            return decision;
        }

        // ---- POSTFLOP EVALUATION ----
        double strength = HANDEVALUATOR.handRank(holeCard1, holeCard2, BOT_COMMUNITY_CARDS, opponentsCount);
        double ppot = HANDPOTENTIAL.ppot_raw(holeCard1, holeCard2, BOT_COMMUNITY_CARDS, true);
        double npot = HANDPOTENTIAL.getLastNPot();
        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        // Scare card detection (when new street reveals)
        if (street != previousStreet) {
            if (previousStrength != -1.0) {
                scareCardDetected = ((effectiveStrength - previousStrength) < -0.15);
            }
            previousPpot = ppot;
            previousStrength = effectiveStrength;
            previousStreet = street;

            // Generate multi-street plan on flop (skilled bots only)
            if (street == Crupier.FLOP && skillLevel != Skill.RECREATIONAL) {
                generateStreetPlan(effectiveStrength, ppot);
            }
        }

        BoardTexture boardTexture = calculateFullBoardTexture();
        double pot = dealer.getBote_total();
        double callCost = dealer.getApuesta_actual() - cpuPlayer.getBet();
        float spr = cpuPlayer.getStack() / (pot > 0 ? (float) pot : 1.0f);

        // ---- POT COMMITMENT ----
        boolean potCommitted = false;
        if (callCost > 0) {
            float commitAdjust = (skillLevel == Skill.RECREATIONAL) ? -0.05f : 0f;
            if (spr <= 2.0f && effectiveStrength >= (0.75 + commitAdjust)) {
                potCommitted = true;
            } else if (spr <= 4.0f && effectiveStrength >= (0.85 + commitAdjust)) {
                potCommitted = true;
            }
        }

        // ---- WIN PROBABILITY with adjustments ----
        double winProb = effectiveStrength;
        OpponentTracker targetStats = getPrimaryOpponentStats();

        // Difficulty scaling: EASY bots make worse reads, HARD bots make better reads
        double difficultyNoise = 0.0;
        if (DIFFICULTY == Difficulty.EASY && skillLevel != Skill.RECREATIONAL) {
            // EASY: Even skilled bots make occasional misjudgments
            difficultyNoise = (Helpers.CSPRNG_GENERATOR.nextDouble() * 0.10 - 0.05);
        }

        // COMPLEX LOGIC & TRACKER ANALYSIS (Skilled Bots Only)
        if (skillLevel != Skill.RECREATIONAL) {
            // Respect heavy action
            if (betCount > 1 && street >= Crupier.FLOP) {
                winProb -= 0.10;
            }
            // Multiway fear
            if (activePlayers > 2) {
                winProb -= 0.04 * (activePlayers - 2);
            }
            // Scare card penalty (LAGs ignore it)
            if (scareCardDetected && currentProfile != Profile.LAG) {
                winProb -= 0.12;
            }
            // Out of position penalty
            if (!isInPositionPostflop()) {
                winProb -= 0.03;
            }

            // Analyze Opponent Stats
            if (betCount > 0 && targetStats != null && targetStats.hasEnoughData()) {
                if (targetStats.isNit()) {
                    winProb -= 0.18; // Nits only bet strong
                } else if (targetStats.isManiac()) {
                    winProb += 0.12; // Maniacs bluff often
                } else if (targetStats.isStation()) {
                    winProb -= 0.03; // Stations have it more often since they call wider
                }
            }

            // Sharks get an execution bonus (represent better play)
            if (skillLevel == Skill.SHARK && DIFFICULTY != Difficulty.EASY) {
                winProb += 0.03;
            }
        } else {
            // Recreationals on tilt overvalue their hands
            if (onTilt) {
                winProb += 0.10;
            }
        }

        winProb = Math.max(0.0, Math.min(1.0, winProb + difficultyNoise));

        // ---- IMPLIED ODDS ----
        double impliedPot = pot;
        boolean safeDraw = (npot < 0.15);

        if (street < Crupier.RIVER && ppot > 0.15 && strength < 0.50 && safeDraw) {
            // Scale implied odds by draw quality
            double impliedMultiplier = Math.min(0.25, ppot * 0.5);
            impliedPot += (cpuPlayer.getStack() * impliedMultiplier);
        }

        // ---- EV CALCULATIONS ----
        double evCall = (callCost > 0)
                ? (winProb * (impliedPot + callCost)) - ((1.0 - winProb) * callCost)
                : 0;

        // Fold Equity based on opponent tracking and board texture
        double foldEquity = calculateFoldEquity(targetStats, boardTexture, betCount, street);

        double raiseAmount = getBetSize();
        double raiseCost = raiseAmount - cpuPlayer.getBet();

        // Corrected EV raise formula
        double evRaise = (foldEquity * pot)
                + ((1.0 - foldEquity) * ((winProb * (pot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        // ---- DECISION LOGIC ----
        int decision = Player.FOLD;

        if (betCount == 0) {
            decision = decisionWhenCheckedTo(effectiveStrength, evCall, evRaise, ppot, npot,
                    foldEquity, street, activePlayers, boardTexture, betCount);
        } else {
            decision = decisionWhenFacingBet(effectiveStrength, evCall, evRaise, ppot, npot,
                    foldEquity, pot, callCost, spr, street, betCount, activePlayers,
                    potCommitted, safeDraw, boardTexture);
        }

        // Track if we're building an aggressive line
        if (decision == Player.BET) {
            aggressiveLine = true;
        }

        return decision;
    }

    // =====================================================================
    // POSTFLOP: Checked to us (betCount == 0)
    // =====================================================================
    private int decisionWhenCheckedTo(double effectiveStrength, double evCall, double evRaise,
            double ppot, double npot, double foldEquity,
            int street, int activePlayers, BoardTexture boardTexture, int betCount) {

        // --- CHECK-RAISE TRAP ---
        if (planCheckRaise && effectiveStrength >= 0.82 && street < Crupier.RIVER) {
            // Abort check-raise on very wet boards (too dangerous)
            if (boardTexture.totalScore <= 3) {
                planCheckRaise = false; // Will be consumed next time we face a bet
                return Player.CHECK;
            }
            planCheckRaise = false; // Board too wet, just bet normally
        }

        // --- SLOWPLAY ---
        if (slowPlay && effectiveStrength >= 0.90 && street < Crupier.RIVER) {
            // Cancel slowplay on wet boards (scare card could come)
            if (boardTexture.totalScore >= 3) {
                slowPlay = false;
                // Fall through to normal decision
            } else {
                return Player.CHECK;
            }
        }

        // --- RIVER DECISIONS ---
        if (street == Crupier.RIVER) {
            if (effectiveStrength >= 0.65) {
                return Player.BET; // Value bet
            }

            // Bluff with busted draws (had potential on earlier streets)
            if (skillLevel != Skill.RECREATIONAL && previousPpot > 0.18 && effectiveStrength < 0.30) {
                if (foldEquity > 0.15 && Helpers.CSPRNG_GENERATOR.nextInt(100) < 30) {
                    return Player.BET;
                }
            }

            // Bluff at end of aggressive line
            if (skillLevel != Skill.RECREATIONAL && aggressiveLine && effectiveStrength < 0.35 && foldEquity > 0.20) {
                if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 25) {
                    return Player.BET;
                }
            }

            // Blocker bluff (skilled only)
            if (skillLevel == Skill.SHARK && effectiveStrength < 0.30 && foldEquity > 0.25) {
                boolean holdsBlocker = (holeCard1.getRank() >= 10 || holeCard2.getRank() >= 10);
                if (holdsBlocker) {
                    return Player.BET;
                }
            }

            return Player.CHECK;
        }

        // --- C-BET LOGIC (adjusted by opponents and board) ---
        if (cBetInitiative && street == Crupier.FLOP) {
            int cbetChance;
            if (skillLevel == Skill.RECREATIONAL) {
                cbetChance = 80; // Recreationals c-bet a lot (they raised, they bet)
            } else {
                // Base c-bet frequency adjusted by number of opponents
                if (activePlayers <= 2) {
                    cbetChance = 70;
                } else if (activePlayers == 3) {
                    cbetChance = 48;
                } else {
                    cbetChance = 28;
                }
                // Reduce on wet boards
                if (boardTexture.totalScore >= 4) {
                    cbetChance -= 15;
                }
                // Increase with strong hands
                if (effectiveStrength > 0.65) {
                    cbetChance += 20;
                }
            }

            if (Helpers.CSPRNG_GENERATOR.nextInt(100) < cbetChance) {
                cBetInitiative = false;
                return Player.BET;
            }
            cBetInitiative = false;
        }

        // --- MULTI-STREET PLAN EXECUTION ---
        if (streetPlan != PLAN_NONE && skillLevel != Skill.RECREATIONAL) {
            int streetsInPlan = street - streetPlanStartStreet;
            switch (streetPlan) {
                case PLAN_BET_BET_BET:
                    if (effectiveStrength > 0.50 || (foldEquity > 0.20 && effectiveStrength < 0.30)) {
                        return Player.BET;
                    }
                    break;
                case PLAN_BET_CHECK_BET:
                    if (streetsInPlan == 0 || streetsInPlan == 2) {
                        if (effectiveStrength > 0.45) {
                            return Player.BET;
                        }
                    } else {
                        return Player.CHECK; // Check turn for pot control
                    }
                    break;
                case PLAN_CHECK_CALL:
                    return Player.CHECK; // Will call if bet comes
            }
        }

        // --- STANDARD BETTING ---
        if (evRaise > 0 && effectiveStrength > 0.55) {
            return Player.BET;
        }

        // Recreational on tilt: bet wider
        if (skillLevel == Skill.RECREATIONAL && onTilt && effectiveStrength > 0.35) {
            return Player.BET;
        }

        return Player.CHECK;
    }

    // =====================================================================
    // POSTFLOP: Facing a bet (betCount > 0)
    // =====================================================================
    private int decisionWhenFacingBet(double effectiveStrength, double evCall, double evRaise,
            double ppot, double npot, double foldEquity,
            double pot, double callCost, float spr,
            int street, int betCount, int activePlayers,
            boolean potCommitted, boolean safeDraw,
            BoardTexture boardTexture) {

        cBetInitiative = false;

        // No raising past max bet count
        if (betCount >= Bot.MAX_BET_COUNT) {
            evRaise = -9999;
        }

        double betRatio = callCost / (pot > 0 ? pot : 1);

        // --- CHECK-RAISE EXECUTION ---
        if (planCheckRaise && effectiveStrength >= 0.80 && betCount == 1) {
            planCheckRaise = false;
            return Player.BET; // This is a raise since betCount > 0
        }
        planCheckRaise = false; // Expired

        // --- FLOATING (call with weak hand in position to steal later) ---
        if (floatPlay && street == Crupier.FLOP && betCount == 1) {
            floatPlay = false; // Consumed
            if (callCost <= pot * 0.6) { // Only float reasonable bets
                return Player.CHECK; // Call the bet
            }
        }
        if (!floatPlay && canFloat(effectiveStrength, betCount, street, boardTexture)) {
            floatPlay = true;
            return Player.CHECK; // Call with float intention
        }

        // --- MDF / FEAR ADJUSTMENT by profile ---
        double adjustedEvCall = evCall;
        if (betRatio > 0.50) {
            if (currentProfile == Profile.STATION) {
                adjustedEvCall += pot * 0.15; // Stations over-call
            } else if (currentProfile == Profile.NIT) {
                adjustedEvCall -= pot * 0.20; // Nits over-fold
            } else if (skillLevel == Skill.REGULAR && effectiveStrength < 0.70) {
                adjustedEvCall -= pot * 0.10; // Regs respect big bets
            }
        }

        // Recreationals on tilt call more
        if (skillLevel == Skill.RECREATIONAL && onTilt) {
            adjustedEvCall += pot * 0.12;
        }

        // --- POT COMMITTED: Shove or call ---
        if (potCommitted) {
            // With very strong hands, consider re-raising (shoving)
            if (effectiveStrength >= 0.92 && evRaise > 0 && currentProfile != Profile.STATION) {
                return Player.BET;
            }
            return Player.CHECK; // Call (pot committed)
        }

        // --- RAISE FOR VALUE ---
        if (evRaise > adjustedEvCall && evRaise > 0 && effectiveStrength > 0.85
                && currentProfile != Profile.STATION && betCount < MAX_BET_COUNT) {
            return Player.BET;
        }

        // --- SQUEEZE PLAY (preflop-like but on flop with multiple callers) ---
        if (skillLevel == Skill.SHARK && betCount == 1 && activePlayers > 3
                && effectiveStrength > 0.60 && foldEquity > 0.25) {
            if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 20) {
                return Player.BET; // Semi-bluff raise
            }
        }

        // --- CALL with equity ---
        if (adjustedEvCall > 0) {
            return Player.CHECK; // Call
        }

        // --- DRAW ODDS ---
        if (ppot > 1.5 * potOdds() && safeDraw && street < Crupier.RIVER) {
            return Player.CHECK; // Call for draw
        }

        // --- MULTI-STREET PLAN: Call down ---
        if (streetPlan == PLAN_CHECK_CALL && effectiveStrength > 0.40
                && skillLevel != Skill.RECREATIONAL && betRatio < 0.75) {
            return Player.CHECK; // Call as part of trap plan
        }

        // --- RECREATIONAL LOOSE CALLS ---
        if (skillLevel == Skill.RECREATIONAL && currentProfile == Profile.STATION && effectiveStrength > 0.25) {
            return Player.CHECK; // Stations call with almost anything
        }

        return Player.FOLD;
    }

    // =====================================================================
    // FOLD EQUITY CALCULATION
    // =====================================================================
    private double calculateFoldEquity(OpponentTracker targetStats, BoardTexture boardTexture,
            int betCount, int street) {
        if (skillLevel == Skill.RECREATIONAL) {
            return 0.0; // Recreationals don't think about fold equity
        }

        double foldEquity = 0.20; // Base

        // Board texture: Easier to fold on dry boards
        if (boardTexture.totalScore <= 1) {
            foldEquity += 0.08;
        } else if (boardTexture.totalScore >= 4) {
            foldEquity -= 0.08;
        }

        // Less fold equity on later streets (people are committed)
        if (street == Crupier.RIVER) {
            foldEquity -= 0.05;
        }

        // More fold equity with fewer bets (first aggression)
        if (betCount == 0) {
            foldEquity += 0.05;
        } else if (betCount >= 2) {
            foldEquity -= 0.10;
        }

        // Opponent-specific adjustments
        if (targetStats != null && targetStats.hasEnoughData()) {
            if (targetStats.isStation()) {
                foldEquity = 0.0; // NEVER bluff a calling station
            } else if (targetStats.isNit()) {
                foldEquity += 0.15; // Nits fold easily
            } else if (targetStats.isManiac()) {
                foldEquity -= 0.10; // Maniacs don't fold much
            }
        }

        // Difficulty scaling
        if (DIFFICULTY == Difficulty.EASY) {
            foldEquity *= 0.7; // Easy bots underestimate fold equity
        } else if (DIFFICULTY == Difficulty.HARD) {
            foldEquity *= 1.15; // Hard bots are better at judging folds
        }

        return Math.max(0.0, Math.min(0.65, foldEquity));
    }

    // =====================================================================
    // FLOATING LOGIC
    // =====================================================================
    private boolean canFloat(double effectiveStrength, int betCount, int street, BoardTexture boardTexture) {
        if (skillLevel == Skill.RECREATIONAL || currentProfile == Profile.NIT) {
            return false;
        }
        // Float conditions: in position, flop, single bet, dry board, mediocre hand
        return (skillLevel == Skill.SHARK || currentProfile == Profile.LAG)
                && isInPositionPostflop()
                && betCount == 1
                && street == Crupier.FLOP
                && boardTexture.totalScore <= 2
                && effectiveStrength > 0.25
                && effectiveStrength < 0.55
                && Helpers.CSPRNG_GENERATOR.nextInt(100) < 18;
    }

    // =====================================================================
    // MULTI-STREET PLAN GENERATION
    // =====================================================================
    private void generateStreetPlan(double effectiveStrength, double ppot) {
        if (DIFFICULTY == Difficulty.EASY) {
            streetPlan = PLAN_NONE; // Easy bots don't plan ahead
            return;
        }

        streetPlanStartStreet = Crupier.FLOP;

        if (effectiveStrength >= 0.88) {
            // Monster hand: Triple barrel for value OR trap
            if (slowPlay || (currentProfile == Profile.TAG && Helpers.CSPRNG_GENERATOR.nextInt(100) < 25)) {
                streetPlan = PLAN_CHECK_CALL; // Trap
            } else {
                streetPlan = PLAN_BET_BET_BET; // Value town
            }
        } else if (effectiveStrength >= 0.60 && effectiveStrength < 0.80) {
            // Medium strength: Pot control
            if (currentProfile != Profile.LAG) {
                streetPlan = PLAN_BET_CHECK_BET;
            } else {
                streetPlan = PLAN_BET_BET_BET; // LAGs barrel through
            }
        } else if (ppot > 0.20 && effectiveStrength < 0.45) {
            // Drawing hand: Bet if we hit, give up if we don't
            streetPlan = PLAN_NONE; // Decide street by street based on equity
        } else if (currentProfile == Profile.LAG && effectiveStrength < 0.30
                && Helpers.CSPRNG_GENERATOR.nextInt(100) < 15) {
            // Shark/LAG bluff line
            streetPlan = PLAN_BET_BET_BET; // Triple barrel bluff
        } else {
            streetPlan = PLAN_NONE;
        }
    }

    // =====================================================================
    // PREFLOP ACTION
    // =====================================================================
    private int calculatePreflopAction(int betCount, int activePlayers) {
        Position pos = determinePosition();
        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);
        int gap = high - low;

        // 1: Premium, 2: Strong, 3: Playable, 4: Marginal, 5: Trash
        int handTier = evaluateHandTier(high, low, isPair, suited, gap);

        // ---- TILT ADJUSTMENT ----
        if (onTilt && skillLevel == Skill.RECREATIONAL) {
            if (handTier == 5) {
                handTier = 4;
            }  // Play trash as marginal
            if (handTier == 4) {
                handTier = 3;
            }  // Play marginal as playable
        }

        // ---- TABLE SIZE ADJUSTMENT (skilled bots only) ----
        if (skillLevel != Skill.RECREATIONAL && DIFFICULTY != Difficulty.EASY) {
            // Tighten up at larger tables
            if (activePlayers >= 7 && pos == Position.EARLY && handTier == 3) {
                handTier = 4;
            }
            // Loosen up at short tables
            if (activePlayers <= 4 && handTier == 4) {
                handTier = 3;
            }
            if (activePlayers <= 3 && handTier == 5 && high >= 8) {
                handTier = 4;
            }
        }

        // ---- STATIONS: Call almost anything ----
        if (currentProfile == Profile.STATION && handTier <= 4 && betCount < 2) {
            return Player.CHECK; // Limp or call
        }

        // ---- SQUEEZE OPPORTUNITY (Sharks/LAGs) ----
        if (betCount == 1 && activePlayers > 3
                && (skillLevel == Skill.SHARK || (currentProfile == Profile.LAG && skillLevel == Skill.REGULAR))
                && handTier <= 3 && DIFFICULTY != Difficulty.EASY) {
            if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 25) {
                return Player.BET; // Squeeze!
            }
        }

        // ---- FACING 3-BET+ ----
        if (betCount >= 2) {
            if (handTier == 1) {
                return Player.BET; // 4-bet with premium
            }
            if (handTier == 2 && skillLevel == Skill.RECREATIONAL) {
                return Player.CHECK; // Fish call with strong hands
            }
            // Sharks can flat some strong hands to trap
            if (handTier == 2 && skillLevel == Skill.SHARK
                    && Helpers.CSPRNG_GENERATOR.nextInt(100) < 35) {
                return Player.CHECK; // Flat call to trap
            }
            return Player.FOLD;
        }

        // ---- FACING SINGLE RAISE ----
        if (betCount == 1) {
            if (handTier == 1) {
                return Player.BET; // 3-bet
            }
            if (handTier <= 3) {
                return Player.CHECK; // Call
            }
            // LAGs sometimes call with tier 4 hands in position
            if (handTier == 4 && currentProfile == Profile.LAG
                    && (pos == Position.LATE || pos == Position.BLINDS)) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

        // ---- UNOPENED POT ----
        switch (pos) {
            case EARLY:
                if (handTier <= 2) {
                    return Player.BET;
                }
                if (handTier == 3 && currentProfile != Profile.NIT) {
                    return Player.BET;
                }
                // Recreationals sometimes open limp in early position
                if (skillLevel == Skill.RECREATIONAL && handTier <= 4
                        && Helpers.CSPRNG_GENERATOR.nextInt(100) < 30) {
                    return Player.CHECK; // Open limp
                }
                return Player.FOLD;
            case MIDDLE:
                if (handTier <= 3) {
                    return Player.BET;
                }
                if (handTier == 4 && currentProfile == Profile.LAG) {
                    return Player.BET;
                }
                return Player.FOLD;
            case LATE:
                if (handTier <= 4 && currentProfile != Profile.NIT) {
                    return Player.BET;
                }
                if (currentProfile == Profile.NIT && handTier <= 3) {
                    return Player.BET;
                }
                // Steal attempt with trash by LAGs/Sharks
                if (handTier == 5 && (currentProfile == Profile.LAG || skillLevel == Skill.SHARK)
                        && Helpers.CSPRNG_GENERATOR.nextInt(100) < 20) {
                    return Player.BET;
                }
                return Player.FOLD;
            case BLINDS:
                if (handTier <= 3 && currentProfile != Profile.NIT) {
                    return Player.BET;
                }
                if (handTier == 4 && currentProfile == Profile.LAG) {
                    return Player.BET;
                }
                return Player.CHECK; // Check from BB
            default:
                return Player.FOLD;
        }
    }

    // =====================================================================
    // HAND TIER EVALUATION (Preflop)
    // =====================================================================
    private int evaluateHandTier(int high, int low, boolean isPair, boolean suited, int gap) {
        if (isPair) {
            if (high >= 10) {
                return 1;  // QQ+
            }
            if (high >= 8) {
                return 2;  // TT, JJ
            }
            if (high >= 5) {
                return 3;  // 55-99
            }
            return 4;        // 22-44
        }

        if (high == 12) { // Ace
            if (low >= 11) {
                return 1;  // AK
            }
            if (low >= 9) {
                return suited ? 2 : 3;  // AQ, AJ, AT
            }
            if (suited) {
                return low >= 6 ? 3 : 4;  // A6s+ = playable, A2s-A5s = marginal
            }
            return low >= 9 ? 3 : 4;  // ATo+ playable, lower offsuit marginal
        }

        if (high == 11) { // King
            if (low >= 10) {
                return suited ? 2 : 3;  // KQ
            }
            if (suited && low >= 8) {
                return 3;  // KJs, KTs
            }
            if (suited && low >= 5) {
                return 4;  // K5s-K9s
            }
            return low >= 10 ? 4 : 5;
        }

        if (high == 10) { // Queen
            if (low >= 9 && suited) {
                return 3;  // QJs, QTs
            }
            if (low >= 9) {
                return 4;  // QJo, QTo
            }
        }

        // Suited connectors & one-gappers
        if (suited && gap <= 2 && high >= 6) {
            if (gap <= 1 && high >= 8) {
                return 3;  // 89s, 9Ts, etc
            }
            return 4;  // Suited one-gappers or lower suited connectors
        }

        // Broadway offsuit
        if (high >= 9 && low >= 8 && !suited) {
            return 4;
        }

        return 5; // Trash
    }

    // =====================================================================
    // FULL BOARD TEXTURE ANALYSIS
    // =====================================================================
    private BoardTexture calculateFullBoardTexture() {
        BoardTexture tex = new BoardTexture();
        if (BOT_COMMUNITY_CARDS.size() < 3) {
            return tex;
        }

        int numCards = BOT_COMMUNITY_CARDS.size();
        int[] suits = new int[4];
        int[] ranks = new int[15]; // 0-12 for 2-A, with room
        int[] cardRanks = new int[numCards];
        int highCardCount = 0;

        for (int i = 1; i <= numCards; i++) {
            org.alberta.poker.Card c = BOT_COMMUNITY_CARDS.getCard(i);
            suits[c.getSuit()]++;
            int r = c.getRank();
            ranks[r]++;
            cardRanks[i - 1] = r;

            // Count high cards (Q=10, K=11, A=12)
            if (r >= 10) {
                highCardCount++;
            }
        }

        // --- FLUSH DANGER ---
        for (int s : suits) {
            if (s >= 4) {
                tex.flushDanger = 3; // 4-flush on board (flush possible with one card)
            } else if (s == 3) {
                tex.flushDanger = Math.max(tex.flushDanger, 2);
            } else if (s == 2) {
                tex.flushDanger = Math.max(tex.flushDanger, 1);
            }
        }

        // --- STRAIGHT DANGER (connectivity) ---
        // Sort card ranks
        int[] sorted = java.util.Arrays.copyOf(cardRanks, numCards);
        java.util.Arrays.sort(sorted);

        int maxRun = 1;
        int currentRun = 1;
        int twoGapCount = 0;

        for (int i = 1; i < sorted.length; i++) {
            int diff = sorted[i] - sorted[i - 1];
            if (diff == 0) {
                continue; // Skip pairs
            }
            if (diff == 1) {
                currentRun++;
                maxRun = Math.max(maxRun, currentRun);
            } else if (diff == 2) {
                twoGapCount++;
                currentRun = 1;
            } else {
                currentRun = 1;
            }
        }

        // Ace-low connectivity (A-2, A-3)
        boolean hasAce = ranks[12] > 0;
        boolean hasLow = (ranks[0] > 0 || ranks[1] > 0 || ranks[2] > 0);
        if (hasAce && hasLow) {
            twoGapCount++;
        }

        if (maxRun >= 3) {
            tex.straightDanger = 3;  // Three in a row (e.g., 7-8-9)
        } else if (maxRun == 2) {
            tex.straightDanger = twoGapCount > 0 ? 2 : 1;
        } else if (twoGapCount >= 2) {
            tex.straightDanger = 1;
        }

        // --- PAIRED BOARD ---
        for (int r : ranks) {
            if (r >= 2) {
                tex.isPaired = true;
                break;
            }
        }

        // --- HIGH CARDS ---
        tex.hasHighCards = (highCardCount >= 2);

        // --- TOTAL SCORE ---
        tex.totalScore = tex.flushDanger + tex.straightDanger
                + (tex.isPaired ? 1 : 0)
                + (tex.hasHighCards ? 1 : 0);

        return tex;
    }

    // =====================================================================
    // POSITION & UTILITIES
    // =====================================================================
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

    // --- THE "EYES" OF THE BOT ---
    private OpponentTracker getPrimaryOpponentStats() {
        Player lastAggressor = GameFrame.getInstance().getCrupier().getLast_aggressor();
        if (lastAggressor != null && TRACKER_MEMORY.containsKey(lastAggressor.getNickname())) {
            return TRACKER_MEMORY.get(lastAggressor.getNickname());
        }
        return null;
    }

    // --- API CONVERTERS (Prevents Crupier Compilation Errors) ---
    public static int coronaCardSuit2LokiCardSuit(Card carta) {
        return Bot.SUITS.indexOf(carta.getPalo());
    }

    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(int carta) {
        int v = (carta - 1) % 13;
        int val = (v == 0 ? 14 : v + 1);
        String palo = Card.PALOS[(int) ((float) (carta - 1) / 13)];
        return new org.alberta.poker.Card(val - 2, Bot.SUITS.indexOf(palo));
    }

    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(Integer carta) {
        return coronaIntegerCard2LokiCard(carta.intValue());
    }

    public static org.alberta.poker.Card coronaCard2LokiCard(Card carta) {
        return new org.alberta.poker.Card(carta.getValorNumerico() - 2, Bot.coronaCardSuit2LokiCardSuit(carta));
    }
}
