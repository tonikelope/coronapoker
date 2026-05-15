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

import com.tonikelope.coronapoker.bot.context.BotPlayerView;
import com.tonikelope.coronapoker.bot.context.DealerView;
import com.tonikelope.coronapoker.bot.eval.AlbertaEvaluatorAdapter;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.Potential;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bot {

    public static final String SUITS = "TDCP";
    public static final int MAX_BET_COUNT = 3;
    public static final int BOT_THINK_TIME = 1500;

    // Core Alberta Engine Tools (retained as statics so legacy Crupier showdown paths keep
    // working; new bot logic accesses them via the EVALUATOR abstraction below).
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    /** Default evaluator shared by every Bot instance; wraps the static singletons above. */
    public static final BotEvaluator EVALUATOR = new AlbertaEvaluatorAdapter(HANDEVALUATOR, HANDPOTENTIAL);

    // Universal Opponent Memory Tracker
    public static final Map<String, OpponentTracker> TRACKER_MEMORY = new ConcurrentHashMap<>();

    // Global difficulty setting
    public static volatile Difficulty DIFFICULTY = Difficulty.MEDIUM;

    public enum Difficulty {
        EASY, MEDIUM, HARD, EXPERT
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

    private static final int PLAN_NONE = 0;
    private static final int PLAN_BET_BET_BET = 1;
    private static final int PLAN_BET_CHECK_BET = 2;
    private static final int PLAN_CHECK_CALL = 3;

    private static final double STRENGTH_NUT_TRAP = 0.92;
    private static final double STRENGTH_VALUE_RAISE = 0.85;
    private static final double STRENGTH_RIVER_VALUE = 0.65;
    private static final double STRENGTH_VALUE_BET = 0.55;
    private static final double STRENGTH_VALUE_BET_DRAW = 0.50;
    private static final double STRENGTH_CALLDOWN_TRAP = 0.40;

    private static final double SCARE_CARD_DROP = 0.15;
    private static final double FOLD_EQ_CAP = 0.65;
    private static final double POT_COMMIT_LOW_SPR_EQ = 0.75;
    private static final double POT_COMMIT_HIGH_SPR_EQ = 0.85;

    private static final double WET_BOARD_OVERSIZE_EQ = 0.85;
    private static final double WET_BOARD_VALUE_FRACTION = 0.70;
    private static final double WET_BOARD_BLOCK_FRACTION = 0.40;
    private static final double SEMIWET_BOARD_FRACTION = 0.55;
    private static final double DRY_BOARD_FRACTION = 0.33;
    private static final double RIVER_OVERBET_FRACTION = 1.25;

    // --- Board texture result ---
    private static class BoardTexture {

        int flushDanger;
        int straightDanger;
        boolean isPaired;
        boolean hasHighCards;
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

    private volatile BotPlayerView cpuPlayer = null;
    private volatile DealerView dealer = null;
    private volatile BotEvaluator evaluator = EVALUATOR;
    private volatile java.util.Random rng = null;
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
    private volatile double lastEffectiveStrength = 0.5;

    private volatile Position cachedPosition = Position.UNKNOWN;
    private volatile BoardTexture cachedTexture = null;
    private volatile int cachedTextureBoardSize = -1;
    private volatile int cachedStrengthStreet = -1;
    private volatile int cachedStrengthOpponents = -1;
    private volatile double cachedStrength = 0.0;
    private volatile int cachedPotentialStreet = -1;
    private volatile Potential cachedPotential = null;

    // Advanced state
    private volatile boolean planCheckRaise = false;
    private volatile boolean floatPlay = false;
    private volatile int streetPlan = PLAN_NONE;
    private volatile int streetPlanStartStreet = -1;
    private volatile int consecutiveLosses = 0;
    private volatile boolean onTilt = false;
    private volatile double previousPpot = 0.0;
    private volatile boolean aggressiveLine = false;

    private static final Logger LOGGER = Logger.getLogger(Bot.class.getName());

    public Bot(RemotePlayer player) {
        this((BotPlayerView) player);
    }

    public Bot(BotPlayerView player) {
        this.cpuPlayer = player;
        assignPersonality();
    }

    /**
     * Inject the dealer view and evaluator used by this bot. Tests and the
     * offline harness call this to bypass {@code GameFrame.getInstance()}.
     */
    public void setContext(DealerView dealer, BotEvaluator evaluator) {
        this.dealer = dealer;
        if (evaluator != null) {
            this.evaluator = evaluator;
        }
    }

    /**
     * Inject a seeded RNG for deterministic replay; passing null restores the
     * shared {@link Helpers#CSPRNG_GENERATOR} default.
     */
    public void setRng(java.util.Random rng) {
        this.rng = rng;
    }

    private int randInt(int bound) {
        java.util.Random r = rng;
        return (r != null ? r : Helpers.CSPRNG_GENERATOR).nextInt(bound);
    }

    private float randFloat() {
        java.util.Random r = rng;
        return (r != null ? r : Helpers.CSPRNG_GENERATOR).nextFloat();
    }

    private double randDouble() {
        java.util.Random r = rng;
        return (r != null ? r : Helpers.CSPRNG_GENERATOR).nextDouble();
    }

    private DealerView dealer() {
        DealerView d = dealer;
        if (d == null) {
            d = GameFrame.getInstance().getCrupier();
            dealer = d;
        }
        return d;
    }

    private int[] currentBoard() {
        DealerView d = dealer();
        int n = d.getBoardSize();
        int[] b = new int[n];
        for (int i = 0; i < n; i++) {
            b[i] = d.getBoardCardIndex(i);
        }
        return b;
    }

    /**
     * Verbose Logger for Debugging BOT Decisions
     */
    private void logVerbose(String message) {
        String botId = cpuPlayer != null ? cpuPlayer.getNickname() : "UnknownBot";

        String formattedMessage = String.format("[BOT AI] [%s] [%s-%s] %s",
                botId, skillLevel.name(), currentProfile.name(), message);

        LOGGER.log(Level.INFO, formattedMessage);
    }

    private void assignPersonality() {
        int skillRoll = randInt(100);
        int styleRoll = randInt(100);

        // Skill mix per difficulty. Roll < recThreshold → RECREATIONAL;
        // < regThreshold → REGULAR; otherwise → SHARK.
        //   EASY:    60 rec  / 32 reg /  8 shark   ("fun fish-fest")
        //   MEDIUM:  25 rec  / 55 reg / 20 shark   ("casual home game")
        //   HARD:    10 rec  / 50 reg / 40 shark   ("experienced players")
        //   EXPERT:   0 rec  / 35 reg / 65 shark   ("professional table")
        int recThreshold, regThreshold;
        switch (DIFFICULTY) {
            case EASY:
                recThreshold = 60;
                regThreshold = 92;
                break;
            case HARD:
                recThreshold = 10;
                regThreshold = 60;
                break;
            case EXPERT:
                recThreshold = 0;
                regThreshold = 35;
                break;
            default: // MEDIUM
                recThreshold = 25;
                regThreshold = 80;
                break;
        }

        if (skillRoll < recThreshold) {
            skillLevel = Skill.RECREATIONAL;
        } else if (skillRoll < regThreshold) {
            skillLevel = Skill.REGULAR;
        } else {
            skillLevel = Skill.SHARK;
        }

        if (skillLevel == Skill.RECREATIONAL) {
            // Recreational sub-distribution depends on difficulty: EASY leans
            // hard into STATIONs (fish-fest feel), higher difficulties keep
            // the original balanced mix so MEDIUM/HARD do not inherit too many
            // calling-station limpers from a small rec slice.
            int stationCut, lagCut, tagCut;
            switch (DIFFICULTY) {
                case EASY:
                    stationCut = 75; lagCut = 87; tagCut = 96; // 75/12/9/4 (less LAG to drop PFR)
                    break;
                case MEDIUM:
                    stationCut = 55; lagCut = 78; tagCut = 92; // 55/23/14/8
                    break;
                default: // HARD / EXPERT (rec is tiny anyway)
                    stationCut = 45; lagCut = 73; tagCut = 90; // 45/28/17/10
                    break;
            }
            if (styleRoll < stationCut) {
                baseProfile = Profile.STATION;
            } else if (styleRoll < lagCut) {
                baseProfile = Profile.LAG;
            } else if (styleRoll < tagCut) {
                baseProfile = Profile.TAG;
            } else {
                baseProfile = Profile.NIT;
            }
        } else if (skillLevel == Skill.REGULAR) {
            if (styleRoll < 60) {
                baseProfile = Profile.TAG;
            } else if (styleRoll < 80) {
                baseProfile = Profile.LAG;
            } else if (styleRoll < 95) {
                baseProfile = Profile.NIT;
            } else {
                baseProfile = Profile.STATION;
            }
        } else {
            if (styleRoll < 55) {
                baseProfile = Profile.TAG;
            } else {
                baseProfile = Profile.LAG;
            }
        }
        currentProfile = baseProfile;
        logVerbose("Personality assigned. Base: " + baseProfile.name());
    }

    private void adjustProfileElasticity() {
        DealerView dealer = dealer();
        float stack = cpuPlayer.getStack();
        float blindsCost = dealer.getCiega_grande() + dealer.getCiega_pequeña();
        float mRatio = stack / (blindsCost > 0 ? blindsCost : 1);

        if (skillLevel == Skill.RECREATIONAL) {
            currentProfile = onTilt ? Profile.LAG : baseProfile;
            if (onTilt) {
                logVerbose("Player is on TILT. Switched to LAG.");
            }
            return;
        }

        if (mRatio < 8.0f) {
            currentProfile = Profile.TAG;
            logVerbose("Short stack detected (M < 8). Adapting to push/fold TAG.");
            return;
        }

        if (mRatio < 15.0f && baseProfile == Profile.LAG) {
            currentProfile = Profile.TAG;
            logVerbose("Medium-short stack. LAG tightening up to TAG.");
            return;
        }

        if (mRatio > 50.0f && baseProfile == Profile.NIT) {
            currentProfile = Profile.TAG;
            logVerbose("Deep stack. NIT loosening up to TAG.");
            return;
        }
        currentProfile = baseProfile;
    }

    public void resetBot() {
        holeCard1 = new org.alberta.poker.Card(cpuPlayer.getHoleCard1Index());
        holeCard2 = new org.alberta.poker.Card(cpuPlayer.getHoleCard2Index());
        adjustProfileElasticity();

        if (skillLevel == Skill.RECREATIONAL) {
            slowPlay = false;
        } else if (skillLevel == Skill.SHARK) {
            slowPlay = randInt(100) < 20;
        } else {
            slowPlay = randInt(100) < (currentProfile == Profile.TAG ? 12 : 4);
        }

        if (slowPlay) {
            logVerbose("Decided to potentially slowplay this hand.");
        }

        planCheckRaise = false;
        floatPlay = false;
        cBetInitiative = false;
        previousStrength = -1.0;
        previousStreet = Crupier.PREFLOP;
        scareCardDetected = false;
        streetPlan = PLAN_NONE;
        streetPlanStartStreet = -1;
        previousPpot = 0.0;
        aggressiveLine = false;
        lastEffectiveStrength = 0.5;
        cachedPosition = Position.UNKNOWN;
        cachedTexture = null;
        cachedTextureBoardSize = -1;
        cachedStrengthStreet = -1;
        cachedStrengthOpponents = -1;
        cachedStrength = 0.0;
        cachedPotentialStreet = -1;
        cachedPotential = null;

        if (skillLevel == Skill.RECREATIONAL && consecutiveLosses >= 2) {
            onTilt = randInt(100) < (30 + consecutiveLosses * 10);
            if (onTilt) {
                logVerbose("Recreational bot has gone on TILT.");
            }
        } else {
            onTilt = false;
        }
    }

    public void recordHandResult(boolean won) {
        if (won) {
            consecutiveLosses = 0;
            onTilt = false;
        } else {
            consecutiveLosses++;
        }
    }

    public float getBetSize() {
        return getBetSize(lastEffectiveStrength);
    }

    public float getBetSize(double effectiveStrength) {
        DealerView dealer = dealer();
        float pot = dealer.getBote_total();
        float currentBet = dealer.getApuesta_actual();
        float minRaise = Helpers.float1DSecureCompare(0f, dealer.getUltimo_raise()) < 0 ? dealer.getUltimo_raise() : dealer.getCiega_grande();
        float bb = dealer.getCiega_grande();
        float targetBet;

        if (dealer.getStreet() == Crupier.PREFLOP) {
            if (dealer.getConta_bet() > 0) {
                targetBet = Math.min(minRaise * 3.0f, (pot > 0 ? pot * 1.5f : minRaise * 4.0f));
            } else {
                int limpers = dealer.getLimpersCount();
                targetBet = (2.5f + (limpers * 1.0f)) * bb;
            }
        } else {
            BoardTexture texture = calculateFullBoardTexture();

            if (skillLevel == Skill.RECREATIONAL) {
                targetBet = pot * (0.45f + (randFloat() * 0.20f));
            } else {
                if (texture.totalScore >= 4) {
                    if (effectiveStrength > WET_BOARD_OVERSIZE_EQ) {
                        targetBet = pot * (float) WET_BOARD_VALUE_FRACTION;
                    } else {
                        targetBet = pot * (float) WET_BOARD_BLOCK_FRACTION;
                    }
                } else if (texture.totalScore >= 2) {
                    targetBet = pot * (float) SEMIWET_BOARD_FRACTION;
                } else {
                    targetBet = pot * (float) DRY_BOARD_FRACTION;
                }

                if (currentProfile == Profile.LAG) {
                    targetBet *= 1.10f;
                } else if (currentProfile == Profile.NIT) {
                    targetBet *= 0.90f;
                }
            }

            if (skillLevel == Skill.SHARK && dealer.getStreet() == Crupier.RIVER && randInt(100) < 18) {
                targetBet = pot * (float) RIVER_OVERBET_FRACTION;
                logVerbose("Applying polarized overbet sizing for River.");
            }
        }

        if (randInt(100) < 30) {
            targetBet *= (1.0f + (randFloat() * 0.30f - 0.15f));
        }
        targetBet = (float) (Math.ceil(Helpers.floatClean(targetBet) / GameFrame.CIEGA_PEQUEÑA) * GameFrame.CIEGA_PEQUEÑA);

        if (Helpers.float1DSecureCompare(currentBet, 0f) == 0 || (dealer.getStreet() == Crupier.PREFLOP && Helpers.float1DSecureCompare(currentBet, bb) == 0)) {
            return Math.max(bb, targetBet);
        } else {
            return Math.max(currentBet + minRaise, currentBet + targetBet);
        }
    }

    public int calculateBotDecision(int opponentsCount) {
        DealerView dealer = dealer();
        int street = dealer.getStreet();
        int activePlayers = opponentsCount + 1;
        int betCount = dealer.getConta_bet();

        if (street == Crupier.PREFLOP) {
            int decision = calculatePreflopAction(betCount, activePlayers);
            if (decision == Player.BET && currentProfile != Profile.STATION) {
                cBetInitiative = true;
            }
            return decision;
        }

        int[] board = currentBoard();
        int hole1 = holeCard1.getIndex();
        int hole2 = holeCard2.getIndex();
        double strength;
        if (cachedStrengthStreet == street && cachedStrengthOpponents == opponentsCount) {
            strength = cachedStrength;
        } else {
            strength = evaluator.handStrengthVsN(hole1, hole2, board, opponentsCount);
            cachedStrength = strength;
            cachedStrengthStreet = street;
            cachedStrengthOpponents = opponentsCount;
        }

        double ppot;
        double npot;
        if (street >= Crupier.RIVER) {
            ppot = 0;
            npot = 0;
        } else if (cachedPotentialStreet == street && cachedPotential != null) {
            ppot = cachedPotential.ppot();
            npot = cachedPotential.npot();
        } else {
            Potential pot = evaluator.potential(hole1, hole2, board, street == Crupier.FLOP);
            cachedPotential = pot;
            cachedPotentialStreet = street;
            ppot = pot.ppot();
            npot = pot.npot();
        }
        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        if (holeCard1.getRank() == holeCard2.getRank()) {
            int overcards = 0;
            for (int idx : board) {
                if ((idx % 13) > holeCard1.getRank()) {
                    overcards++;
                }
            }
            if (overcards >= 2) {
                effectiveStrength -= (0.15 * overcards);
                logVerbose("Overcard Penalty: Pair crushed by " + overcards + " cards.");
            }
        }

        if (strength > 0.50 && strength < 0.80 && holeCard1.getRank() != holeCard2.getRank()) {
            int lowCard = Math.min(holeCard1.getRank(), holeCard2.getRank());
            if (lowCard < 6) {
                effectiveStrength -= 0.12;
                logVerbose("Weak Kicker Penalty applied.");
            }
        }
        effectiveStrength = Math.max(0.10, effectiveStrength);

        if (street != previousStreet) {
            if (previousStrength != -1.0) {
                scareCardDetected = ((effectiveStrength - previousStrength) < -SCARE_CARD_DROP);
            }
            previousPpot = ppot;
            previousStrength = effectiveStrength;
            previousStreet = street;

            if (scareCardDetected) {
                logVerbose("Scare card detected on new street. Equity dropped.");
            }

            if (street == Crupier.FLOP && skillLevel != Skill.RECREATIONAL) {
                generateStreetPlan(effectiveStrength, ppot);
            }

            if (streetPlan != PLAN_NONE && street > Crupier.FLOP) {
                if (activePlayers > 2 && streetPlan == PLAN_BET_BET_BET && effectiveStrength < STRENGTH_VALUE_BET_DRAW) {
                    logVerbose("Aborting PLAN_BET_BET_BET. Too many active players (Multiway panic).");
                    streetPlan = PLAN_NONE;
                }
                if (scareCardDetected && streetPlan == PLAN_BET_BET_BET) {
                    logVerbose("Downgrading PLAN_BET_BET_BET to pot control due to scare card.");
                    streetPlan = PLAN_BET_CHECK_BET;
                }
            }
        }

        BoardTexture boardTexture = calculateFullBoardTexture();
        double pot = dealer.getBote_total();
        double callCost = dealer.getApuesta_actual() - cpuPlayer.getBet();
        float spr = cpuPlayer.getStack() / (pot > 0 ? (float) pot : 1.0f);

        boolean potCommitted = false;
        if (callCost > 0) {
            float commitAdjust = (skillLevel == Skill.RECREATIONAL) ? -0.05f : 0f;
            if ((spr <= 2.0f && effectiveStrength >= (POT_COMMIT_LOW_SPR_EQ + commitAdjust))
                    || (spr <= 4.0f && effectiveStrength >= (POT_COMMIT_HIGH_SPR_EQ + commitAdjust))) {
                potCommitted = true;
                logVerbose("Bot considers itself POT COMMITTED.");
            }
        }

        double winProb = effectiveStrength;
        OpponentTracker targetStats = getPrimaryOpponentStats();
        double difficultyNoise = (DIFFICULTY == Difficulty.EASY && skillLevel != Skill.RECREATIONAL) ? (randDouble() * 0.10 - 0.05) : 0.0;

        if (skillLevel != Skill.RECREATIONAL) {
            if (betCount > 1 && street >= Crupier.FLOP) {
                winProb -= 0.10;
            }
            if (activePlayers > 2) {
                winProb -= 0.04 * (activePlayers - 2);
            }
            if (scareCardDetected && currentProfile != Profile.LAG) {
                winProb -= 0.12;
            }
            if (!isInPositionPostflop()) {
                winProb -= 0.03;
            }

            if (betCount > 0 && targetStats != null && targetStats.hasEnoughData()) {
                if (targetStats.isNit()) {
                    winProb -= 0.18;
                } else if (targetStats.isManiac()) {
                    winProb += 0.12;
                } else if (targetStats.isStation()) {
                    winProb -= 0.03;
                }
            }
            if (skillLevel == Skill.SHARK && DIFFICULTY != Difficulty.EASY) {
                winProb += (DIFFICULTY == Difficulty.EXPERT ? 0.05 : 0.03);
            }
        } else {
            if (onTilt) {
                winProb += 0.10;
            }
        }

        winProb = Math.max(0.0, Math.min(1.0, winProb + difficultyNoise));
        double impliedPot = pot;
        boolean safeDraw = (npot < 0.15);

        if (street < Crupier.RIVER && ppot > 0.15 && strength < 0.50 && safeDraw) {
            impliedPot += (cpuPlayer.getStack() * Math.min(0.25, ppot * 0.5));
        }

        double evCall = (callCost > 0) ? (winProb * (impliedPot + callCost)) - ((1.0 - winProb) * callCost) : 0;
        double foldEquity = calculateFoldEquity(targetStats, boardTexture, betCount, street);
        double raiseAmount = getBetSize(effectiveStrength);
        double raiseCost = raiseAmount - cpuPlayer.getBet();
        double evRaise = (foldEquity * pot) + ((1.0 - foldEquity) * ((winProb * (pot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        int decision = Player.FOLD;
        if (betCount == 0) {
            decision = decisionWhenCheckedTo(effectiveStrength, evCall, evRaise, ppot, npot, foldEquity, street, activePlayers, boardTexture, betCount);
        } else {
            decision = decisionWhenFacingBet(effectiveStrength, evCall, evRaise, ppot, npot, foldEquity, pot, callCost, spr, street, betCount, activePlayers, potCommitted, safeDraw, boardTexture, targetStats);
        }

        if (decision == Player.BET) {
            aggressiveLine = true;
        }
        lastEffectiveStrength = effectiveStrength;
        return decision;
    }

    private int decisionWhenCheckedTo(double effectiveStrength, double evCall, double evRaise, double ppot, double npot, double foldEquity, int street, int activePlayers, BoardTexture boardTexture, int betCount) {

        if (floatPlay && street == Crupier.TURN) {
            logVerbose("Executing Float Bluff follow-through on Turn.");
            floatPlay = false;
            return Player.BET;
        }

        if (slowPlay && effectiveStrength >= STRENGTH_NUT_TRAP && street < Crupier.RIVER) {
            if (boardTexture.totalScore >= 3) {
                logVerbose("Aborting slowplay due to wet board texture.");
                slowPlay = false;
            } else {
                logVerbose("Executing slowplay trap (Check).");
                return Player.CHECK;
            }
        }

        if (street == Crupier.RIVER) {
            if (effectiveStrength >= STRENGTH_RIVER_VALUE) {
                logVerbose("River Value Bet.");
                return Player.BET;
            }
            if (skillLevel == Skill.SHARK && effectiveStrength >= STRENGTH_VALUE_BET
                    && boardTexture.totalScore <= 2 && isInPositionPostflop()
                    && activePlayers <= 2 && randInt(100) < 55) {
                logVerbose("River thin value bet (small).");
                return Player.BET;
            }
            if (skillLevel != Skill.RECREATIONAL && previousPpot > 0.18 && effectiveStrength < 0.30 && foldEquity > 0.15 && randInt(100) < 30) {
                logVerbose("River Bluff (Busted Draw).");
                return Player.BET;
            }
            if (skillLevel != Skill.RECREATIONAL && aggressiveLine && effectiveStrength < 0.35 && foldEquity > 0.20 && randInt(100) < 25) {
                logVerbose("River Bluff (Aggressive Line follow-through).");
                return Player.BET;
            }
            return Player.CHECK;
        }

        if (cBetInitiative && street == Crupier.FLOP) {
            int cbetChance = (skillLevel == Skill.RECREATIONAL) ? 80 : (activePlayers <= 2 ? 70 : (activePlayers == 3 ? 48 : 28));
            if (skillLevel != Skill.RECREATIONAL) {
                if (boardTexture.totalScore >= 4) {
                    cbetChance -= 15;
                } else if (boardTexture.totalScore == 0) {
                    cbetChance += 12;
                }
                if (boardTexture.isPaired) {
                    cbetChance -= 8;
                }
                if (effectiveStrength > 0.65) {
                    cbetChance += 20;
                }
                if ((DIFFICULTY == Difficulty.HARD || DIFFICULTY == Difficulty.EXPERT)
                        && hasRangeAdvantageOverFlop()) {
                    cbetChance += (DIFFICULTY == Difficulty.EXPERT ? 15 : 10);
                    logVerbose("Range advantage detected on flop (high-card top-down).");
                }
            }

            cBetInitiative = false;
            if (randInt(100) < cbetChance) {
                logVerbose("Executing C-Bet.");
                return Player.BET;
            }
        }

        if (street < Crupier.RIVER && skillLevel != Skill.RECREATIONAL && currentProfile != Profile.NIT
                && ppot > 0.30 && npot < 0.15 && effectiveStrength < STRENGTH_VALUE_BET_DRAW
                && foldEquity > 0.18 && boardTexture.totalScore <= 3
                && randInt(100) < (skillLevel == Skill.SHARK ? 45 : 28)) {
            logVerbose("Semi-bluff with strong draw.");
            return Player.BET;
        }

        if (streetPlan != PLAN_NONE && skillLevel != Skill.RECREATIONAL) {
            int streetsInPlan = street - streetPlanStartStreet;
            switch (streetPlan) {
                case PLAN_BET_BET_BET:
                    if (effectiveStrength > STRENGTH_VALUE_BET_DRAW || (foldEquity > 0.20 && effectiveStrength < 0.30)) {
                        logVerbose("Executing PLAN_BET_BET_BET.");
                        return Player.BET;
                    }
                    break;
                case PLAN_BET_CHECK_BET:
                    if (streetsInPlan == 0 || streetsInPlan == 2) {
                        if (effectiveStrength > 0.45) {
                            logVerbose("Executing active phase of PLAN_BET_CHECK_BET.");
                            return Player.BET;
                        }
                    } else {
                        logVerbose("Executing check phase of PLAN_BET_CHECK_BET.");
                        return Player.CHECK;
                    }
                    break;
                case PLAN_CHECK_CALL:
                    logVerbose("Executing check phase of PLAN_CHECK_CALL trap.");
                    return Player.CHECK;
            }
        }

        boolean boardTooScary = boardTexture.totalScore >= 5 && effectiveStrength < STRENGTH_VALUE_RAISE;
        double valueThreshold = activePlayers >= 4 ? STRENGTH_VALUE_BET + 0.10 : STRENGTH_VALUE_BET;
        if (evRaise > 0 && effectiveStrength > valueThreshold && !boardTooScary) {
            logVerbose("Standard Value Bet based on EV.");
            return Player.BET;
        } else if (boardTooScary && evRaise > 0) {
            logVerbose("Value bet blocked: Board texture too dangerous for current strength.");
        }

        if (skillLevel == Skill.RECREATIONAL && onTilt && effectiveStrength > 0.35) {
            logVerbose("Tilt Bet (Recreational overriding EV).");
            return Player.BET;
        }

        logVerbose("No profitable bet found. Checking.");
        return Player.CHECK;
    }

    private int decisionWhenFacingBet(double effectiveStrength, double evCall, double evRaise, double ppot, double npot, double foldEquity, double pot, double callCost, float spr, int street, int betCount, int activePlayers, boolean potCommitted, boolean safeDraw, BoardTexture boardTexture, OpponentTracker targetStats) {
        cBetInitiative = false;
        if (betCount >= Bot.MAX_BET_COUNT) {
            evRaise = -9999;
        }
        double betRatio = callCost / (pot > 0 ? pot : 1);

        if (skillLevel != Skill.RECREATIONAL && currentProfile != Profile.STATION && betCount == 1 && street < Crupier.RIVER) {
            boolean hasOvercards = false;
            if (holeCard1.getRank() == holeCard2.getRank()) {
                DealerView d = dealer();
                int boardSize = d.getBoardSize();
                for (int i = 0; i < boardSize; i++) {
                    if ((d.getBoardCardIndex(i) % 13) > holeCard1.getRank()) {
                        hasOvercards = true;
                        break;
                    }
                }
            }

            if (effectiveStrength >= 0.82 && betRatio < 0.75 && boardTexture.totalScore <= 3 && !hasOvercards) {
                logVerbose("Dynamic Check-Raise executed. Favorable hand and texture.");
                return Player.BET;
            }
        }

        if (floatPlay && street == Crupier.FLOP && betCount == 1) {
            floatPlay = false;
            if (callCost <= pot * 0.6) {
                logVerbose("Calling bet to float the Flop.");
                return Player.CHECK;
            }
        }
        if (!floatPlay && callCost <= pot * 0.6 && canFloat(effectiveStrength, betCount, street, boardTexture)) {
            floatPlay = true;
            logVerbose("Initiating Float Strategy (Calling to bluff later).");
            return Player.CHECK;
        }

        double adjustedEvCall = evCall;
        if (betRatio > 0.50) {
            if (currentProfile == Profile.STATION) {
                adjustedEvCall += pot * 0.15;
            } else if (currentProfile == Profile.NIT) {
                adjustedEvCall -= pot * 0.20;
            } else if (skillLevel == Skill.REGULAR && effectiveStrength < 0.70) {
                adjustedEvCall -= pot * 0.10;
            }
        }
        if (skillLevel == Skill.RECREATIONAL && onTilt) {
            adjustedEvCall += pot * 0.12;
        }

        if (potCommitted) {
            if (effectiveStrength >= STRENGTH_NUT_TRAP && evRaise > 0 && currentProfile != Profile.STATION) {
                logVerbose("Pot committed and very strong. Shoving (Re-raising).");
                return Player.BET;
            }
            logVerbose("Pot committed. Calling down.");
            return Player.CHECK;
        }

        // Threshold for raise-for-value drops on HARD/EXPERT so sharks generate
        // the AF=2-3 aggression industry regulars show, rather than calling down.
        double valueRaiseThreshold = STRENGTH_VALUE_RAISE;
        if (DIFFICULTY == Difficulty.EXPERT) {
            valueRaiseThreshold = 0.72;
        } else if (DIFFICULTY == Difficulty.HARD) {
            valueRaiseThreshold = 0.78;
        }
        if (evRaise > adjustedEvCall && evRaise > 0 && effectiveStrength > valueRaiseThreshold && currentProfile != Profile.STATION && betCount < MAX_BET_COUNT) {
            logVerbose("Raising for value. High EV.");
            return Player.BET;
        }

        // Medium-strength raise band: aggressive aggression-factor booster.
        // Iter 10 cranks rates much higher and drops the evRaise>adjustedEvCall*0.55
        // floor — that guard was rejecting most candidates because EV math is
        // sensitive on marginal hands. The "any positive evRaise" gate plus
        // wider eligibility lifts HARD/EXPERT AF from 1.3 toward the 2-3 target.
        boolean mediumRaiseEligible = effectiveStrength >= 0.40
                && effectiveStrength < valueRaiseThreshold
                && evRaise > 0
                && betCount < MAX_BET_COUNT
                && currentProfile != Profile.STATION
                && currentProfile != Profile.NIT;
        if (mediumRaiseEligible) {
            int chance;
            if (DIFFICULTY == Difficulty.EXPERT) {
                chance = (skillLevel == Skill.SHARK) ? 75
                        : (currentProfile == Profile.LAG) ? 55
                        : (currentProfile == Profile.TAG) ? 45 : 0;
            } else if (DIFFICULTY == Difficulty.HARD) {
                chance = (skillLevel == Skill.SHARK) ? 55
                        : (currentProfile == Profile.LAG) ? 38
                        : (currentProfile == Profile.TAG) ? 30 : 0;
            } else if (DIFFICULTY == Difficulty.MEDIUM) {
                chance = (skillLevel == Skill.SHARK) ? 30
                        : (currentProfile == Profile.LAG) ? 18
                        : (currentProfile == Profile.TAG) ? 10 : 0;
            } else {
                chance = 0; // EASY stays passive
            }
            if (chance > 0 && randInt(100) < chance) {
                logVerbose("Medium-strength raise (AF booster).");
                return Player.BET;
            }
        }

        if (skillLevel == Skill.SHARK && betCount == 1 && activePlayers > 3 && effectiveStrength > 0.60 && foldEquity > 0.25 && randInt(100) < 20) {
            logVerbose("Executing Squeeze Play against multiple callers.");
            return Player.BET;
        }

        if (streetPlan == PLAN_CHECK_CALL) {
            boolean overbetThreshold = betRatio > 1.2;
            boolean nitThreshold = targetStats != null && targetStats.isNit() && betRatio > 0.6;

            if (overbetThreshold || nitThreshold) {
                logVerbose("Pain threshold exceeded (Overbet or strong bet from Nit). Aborting PLAN_CHECK_CALL.");
                streetPlan = PLAN_NONE;
            } else if (effectiveStrength > STRENGTH_CALLDOWN_TRAP && skillLevel != Skill.RECREATIONAL) {
                logVerbose("Executing trap call from PLAN_CHECK_CALL.");
                return Player.CHECK;
            }
        }

        if (adjustedEvCall > 0) {
            logVerbose("Calling due to positive adjusted EV.");
            return Player.CHECK;
        }

        if (ppot > 1.5 * potOdds() && safeDraw && street < Crupier.RIVER) {
            logVerbose("Calling based on implied draw odds.");
            return Player.CHECK;
        }

        if (skillLevel == Skill.RECREATIONAL && currentProfile == Profile.STATION && effectiveStrength > 0.25) {
            logVerbose("Station calling out of profile habit despite negative EV.");
            return Player.CHECK;
        }

        // MDF (Minimum Defense Frequency) bluffcatch guard. Only HARD/EXPERT sharks defend
        // against half-pot-or-smaller river bets when the raw call EV is positive but a
        // profile-driven penalty (nit, scare card) flipped adjustedEvCall negative.
        if (skillLevel == Skill.SHARK
                && (DIFFICULTY == Difficulty.HARD || DIFFICULTY == Difficulty.EXPERT)
                && evCall > 0 && adjustedEvCall <= 0
                && street == Crupier.RIVER
                && betRatio <= 1.0
                && effectiveStrength > 0.30
                && currentProfile != Profile.NIT) {
            double mdf = pot / (pot + callCost);
            if (effectiveStrength > mdf * 0.45) {
                logVerbose("MDF bluffcatch: raw call EV positive, defending despite profile adjustment.");
                return Player.CHECK;
            }
        }

        logVerbose("Folding. No profitable action.");
        return Player.FOLD;
    }

    private double calculateFoldEquity(OpponentTracker targetStats, BoardTexture boardTexture, int betCount, int street) {
        if (skillLevel == Skill.RECREATIONAL) {
            return 0.0;
        }
        double foldEquity = 0.20;

        if (boardTexture.totalScore <= 1) {
            foldEquity += 0.08;
        } else if (boardTexture.totalScore >= 4) {
            foldEquity -= 0.08;
        }

        if (street == Crupier.RIVER) {
            foldEquity -= 0.05;
        }

        if (betCount == 0) {
            foldEquity += 0.05;
        } else if (betCount >= 2) {
            foldEquity -= 0.10;
        }

        if (targetStats != null && targetStats.hasEnoughData()) {
            if (targetStats.isStation()) {
                foldEquity = 0.0;
            } else if (targetStats.isNit()) {
                foldEquity += 0.15;
            } else if (targetStats.isManiac()) {
                foldEquity -= 0.10;
            }
        }

        if (DIFFICULTY == Difficulty.EASY) {
            foldEquity *= 0.7;
        } else if (DIFFICULTY == Difficulty.HARD) {
            foldEquity *= 1.15;
        } else if (DIFFICULTY == Difficulty.EXPERT) {
            foldEquity *= 1.25;
        }

        return Math.max(0.0, Math.min(FOLD_EQ_CAP, foldEquity));
    }

    private boolean canFloat(double effectiveStrength, int betCount, int street, BoardTexture boardTexture) {
        if (skillLevel == Skill.RECREATIONAL || currentProfile == Profile.NIT) {
            return false;
        }
        return (skillLevel == Skill.SHARK || currentProfile == Profile.LAG)
                && isInPositionPostflop()
                && betCount == 1
                && street == Crupier.FLOP
                && boardTexture.totalScore <= 2
                && effectiveStrength > 0.25
                && effectiveStrength < 0.55
                && randInt(100) < 18;
    }

    private void generateStreetPlan(double effectiveStrength, double ppot) {
        if (DIFFICULTY == Difficulty.EASY) {
            streetPlan = PLAN_NONE;
            return;
        }

        streetPlanStartStreet = Crupier.FLOP;

        if (effectiveStrength >= 0.88) {
            if (slowPlay || (currentProfile == Profile.TAG && randInt(100) < 25)) {
                streetPlan = PLAN_CHECK_CALL;
                logVerbose("Generated Street Plan: TRAP (Check-Call).");
            } else {
                streetPlan = PLAN_BET_BET_BET;
                logVerbose("Generated Street Plan: VALUE TOWN (Bet-Bet-Bet).");
            }
        } else if (effectiveStrength >= 0.60 && effectiveStrength < 0.80) {
            if (currentProfile != Profile.LAG) {
                streetPlan = PLAN_BET_CHECK_BET;
                logVerbose("Generated Street Plan: POT CONTROL (Bet-Check-Bet).");
            } else {
                streetPlan = PLAN_BET_BET_BET;
                logVerbose("Generated Street Plan: LAG BARREL (Bet-Bet-Bet).");
            }
        } else if (ppot > 0.20 && effectiveStrength < 0.45) {
            streetPlan = PLAN_NONE;
            logVerbose("Drawing hand. Deciding street by street.");
        } else if (currentProfile == Profile.LAG && effectiveStrength < 0.30 && randInt(100) < 15) {
            streetPlan = PLAN_BET_BET_BET;
            logVerbose("Generated Street Plan: TRIPLE BARREL BLUFF.");
        } else {
            streetPlan = PLAN_NONE;
        }
    }

    private int calculatePreflopAction(int betCount, int activePlayers) {
        Position pos = determinePosition();
        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);
        int gap = high - low;

        int handTier = evaluateHandTier(high, low, isPair, suited, gap);

        if (onTilt && skillLevel == Skill.RECREATIONAL) {
            if (handTier == 5) {
                handTier = 4;
            }
            if (handTier == 4) {
                handTier = 3;
            }
        }

        if (skillLevel != Skill.RECREATIONAL && DIFFICULTY != Difficulty.EASY) {
            if (activePlayers >= 7 && pos == Position.EARLY && handTier == 3) {
                handTier = 4;
            }
            if (activePlayers <= 4 && handTier == 4) {
                handTier = 3;
            }
            if (activePlayers <= 3 && handTier == 5 && high >= 8) {
                handTier = 4;
            }
        }

        if (currentProfile == Profile.STATION && handTier <= 4 && betCount < 2) {
            logVerbose("Preflop Station limp/call.");
            return Player.CHECK;
        }

        DealerView crupier = dealer();
        String myNick = cpuPlayer.getNickname();
        boolean isSB = myNick.equals(crupier.getSb_nick());
        boolean isBB = myNick.equals(crupier.getBb_nick());

        if (betCount == 1 && activePlayers > 3 && (skillLevel == Skill.SHARK || (currentProfile == Profile.LAG && skillLevel == Skill.REGULAR)) && handTier <= 3 && DIFFICULTY != Difficulty.EASY) {
            int squeezeChance = (pos == Position.LATE || pos == Position.BLINDS) ? 35 : 25;
            if (randInt(100) < squeezeChance) {
                logVerbose("Preflop Squeeze.");
                return Player.BET;
            }
        }

        if (betCount >= 3) {
            if (handTier == 1 && low >= 10) {
                logVerbose("Preflop 5-Bet (premium only).");
                return Player.BET;
            }
            logVerbose("Preflop Fold vs 4-Bet.");
            return Player.FOLD;
        }

        if (betCount == 2) {
            if (handTier == 1) {
                logVerbose("Preflop 4-Bet for value.");
                return Player.BET;
            }
            if (handTier == 2) {
                logVerbose("Preflop Call vs 3-Bet (TT/JJ/AQ/KQs).");
                return Player.CHECK;
            }
            if (handTier == 3 && skillLevel != Skill.RECREATIONAL && isPair && high <= 4) {
                logVerbose("Preflop Set-mine call vs 3-Bet (small pair).");
                return Player.CHECK;
            }
            logVerbose("Preflop Fold vs 3-Bet.");
            return Player.FOLD;
        }

        boolean headsUp = (activePlayers == 2);

        if (betCount == 1) {
            if (handTier == 1) {
                logVerbose("Preflop 3-Bet for value.");
                return Player.BET;
            }
            boolean threeBetBluffCandidate = (high == 12 && low <= 3 && suited)
                    || (high == 11 && low == 10 && !suited);
            if (skillLevel == Skill.SHARK && threeBetBluffCandidate
                    && (pos == Position.LATE || pos == Position.BLINDS)
                    && randInt(100) < 30) {
                logVerbose("Preflop 3-Bet bluff (blocker).");
                return Player.BET;
            }
            if (handTier <= 3) {
                logVerbose("Preflop Standard Call.");
                return Player.CHECK;
            }
            if (isBB && handTier == 4) {
                logVerbose("Preflop BB defend vs steal.");
                return Player.CHECK;
            }
            if (handTier == 4 && currentProfile == Profile.LAG && (pos == Position.LATE || pos == Position.BLINDS)) {
                logVerbose("Preflop LAG loose call in position.");
                return Player.CHECK;
            }
            // Heads-up BB defends wider than full-ring. Iteration 6 adds an
            // explicit difficulty offset on top of the profile rates so the
            // overall VPIP gradient slopes the right way: EASY plays loose
            // (lots of defends, calling-station style), EXPERT plays tight
            // (disciplined sharks fold trash to a button open).
            if (headsUp && isBB) {
                int defendChance;
                if (handTier == 4) {
                    defendChance = (currentProfile == Profile.NIT) ? 45
                            : (currentProfile == Profile.STATION) ? 88
                            : (currentProfile == Profile.LAG) ? 78
                            : (skillLevel == Skill.SHARK) ? 65
                            : 62; // TAG default
                } else { // tier 5 trash
                    defendChance = (currentProfile == Profile.NIT) ? 8
                            : (currentProfile == Profile.STATION) ? 55
                            : (currentProfile == Profile.LAG) ? 30
                            : (skillLevel == Skill.SHARK) ? 22
                            : 20; // TAG default
                }
                defendChance = clampPct(defendChance + difficultyLoosenessOffset());
                if (randInt(100) < defendChance) {
                    logVerbose("Preflop HU BB defend vs button raise.");
                    return Player.CHECK;
                }
            }
            logVerbose("Preflop Fold vs Raise.");
            return Player.FOLD;
        }

        if (isSB && betCount == 0) {
            if (handTier <= 4 && currentProfile != Profile.NIT) {
                // Tier-4 marginal hands: HARD/EXPERT TAGs/SHARKs fold a slice
                // rather than open every time. Iter 12 retunes back toward
                // iter-9 values (20% HARD / 33% EXPERT) because iter 10's
                // relaxation pushed HARD VPIP back into 54%, outside target.
                if (handTier == 4 && currentProfile != Profile.STATION
                        && currentProfile != Profile.LAG
                        && (DIFFICULTY == Difficulty.HARD || DIFFICULTY == Difficulty.EXPERT)) {
                    int foldChance = (DIFFICULTY == Difficulty.EXPERT) ? 33 : 22;
                    if (randInt(100) < foldChance) {
                        logVerbose("Preflop HU SB tier-4 selective fold.");
                        return Player.FOLD;
                    }
                }
                logVerbose("Preflop SB folded-to: open wide.");
                return Player.BET;
            }
            // Heads-up button opens trash wider than full-ring cutoff/button but
            // not freely. Iteration 5 trim: previous 60% LAG/SHARK pushed HARD/EXPERT
            // VPIP into the 75-78% range (target 38-50%). New targets put HU button
            // open rates roughly at: NIT 45%, TAG 60%, STATION 70% (with limps),
            // LAG 70%, SHARK 65% — covering the industry 60-80% band.
            if (handTier == 5 && headsUp) {
                int stealChance;
                if (currentProfile == Profile.NIT) {
                    stealChance = 10;
                } else if (currentProfile == Profile.LAG) {
                    stealChance = 42;
                } else if (skillLevel == Skill.SHARK) {
                    stealChance = 32;
                } else if (currentProfile == Profile.TAG) {
                    stealChance = 28;
                } else { // STATION recreational
                    stealChance = 18;
                }
                stealChance = clampPct(stealChance + difficultyLoosenessOffset());
                if (randInt(100) < stealChance) {
                    logVerbose("Preflop HU SB steal (tier 5 wide).");
                    return Player.BET;
                }
            }
            if (handTier == 5 && (currentProfile == Profile.LAG || skillLevel == Skill.SHARK)) {
                int fallbackChance = clampPct(25 + difficultyLoosenessOffset());
                if (fallbackChance > 0 && randInt(100) < fallbackChance) {
                    logVerbose("Preflop SB folded-to: trash steal.");
                    return Player.BET;
                }
            }
            if (currentProfile == Profile.STATION && handTier == 5
                    && randInt(100) < 40) {
                logVerbose("Preflop SB limp-complete.");
                return Player.CHECK;
            }
            logVerbose("Preflop SB fold.");
            return Player.FOLD;
        }

        switch (pos) {
            case EARLY:
                if (handTier <= 2) {
                    logVerbose("Preflop Early Open.");
                    return Player.BET;
                }
                if (handTier == 3 && currentProfile != Profile.NIT) {
                    logVerbose("Preflop Early Loose Open.");
                    return Player.BET;
                }
                if (skillLevel == Skill.RECREATIONAL && handTier <= 4 && randInt(100) < 30) {
                    logVerbose("Preflop Rec open limp.");
                    return Player.CHECK;
                }
                return Player.FOLD;
            case MIDDLE:
                if (handTier <= 3) {
                    logVerbose("Preflop Middle Open.");
                    return Player.BET;
                }
                if (handTier == 4 && currentProfile == Profile.LAG) {
                    logVerbose("Preflop Middle LAG Open.");
                    return Player.BET;
                }
                return Player.FOLD;
            case LATE:
                if (handTier <= 4 && currentProfile != Profile.NIT) {
                    logVerbose("Preflop Steal/Open.");
                    return Player.BET;
                }
                if (currentProfile == Profile.NIT && handTier <= 3) {
                    logVerbose("Preflop Nit Open.");
                    return Player.BET;
                }
                if (handTier == 5 && (currentProfile == Profile.LAG || skillLevel == Skill.SHARK) && randInt(100) < 20) {
                    logVerbose("Preflop Trash Steal (Bluff).");
                    return Player.BET;
                }
                return Player.FOLD;
            case BLINDS:
                if (handTier <= 3 && currentProfile != Profile.NIT) {
                    logVerbose("Preflop BB iso-raise vs limpers.");
                    return Player.BET;
                }
                if (handTier == 4 && currentProfile == Profile.LAG) {
                    logVerbose("Preflop BB LAG iso-raise.");
                    return Player.BET;
                }
                logVerbose("Preflop BB check option.");
                return Player.CHECK;
            default:
                return Player.FOLD;
        }
    }

    private int evaluateHandTier(int high, int low, boolean isPair, boolean suited, int gap) {
        if (isPair) {
            if (high >= 10) {
                return 1;
            }
            if (high >= 8) {
                return 2;
            }
            if (high >= 5) {
                return 3;
            }
            return 4;
        }
        if (high == 12) {
            if (low >= 11) {
                return 1;
            }
            if (low >= 9) {
                return suited ? 2 : 3;
            }
            if (suited) {
                if (low >= 6) {
                    return 3;
                }
                if (low <= 3) {
                    return 4;
                }
                return 4;
            }
            return 4;
        }
        if (high == 11) {
            if (low >= 10) {
                return suited ? 2 : 3;
            }
            if (suited && low >= 8) {
                return 3;
            }
            if (suited && low >= 5) {
                return 4;
            }
            return 5;
        }
        if (high == 10) {
            if (low >= 9) {
                return suited ? 3 : 4;
            }
        }
        if (suited && gap <= 2 && high >= 6) {
            if (gap <= 1 && high >= 8) {
                return 3;
            }
            return 4;
        }
        if (high >= 9 && low >= 8 && !suited) {
            return 4;
        }
        return 5;
    }

    private BoardTexture calculateFullBoardTexture() {
        int numCards = dealer().getBoardSize();
        if (cachedTexture != null && cachedTextureBoardSize == numCards) {
            return cachedTexture;
        }
        BoardTexture tex = computeBoardTexture(numCards);
        cachedTexture = tex;
        cachedTextureBoardSize = numCards;
        return tex;
    }

    private BoardTexture computeBoardTexture(int numCards) {
        BoardTexture tex = new BoardTexture();
        if (numCards < 3) {
            return tex;
        }
        DealerView d = dealer();
        int[] suits = new int[4];
        int[] ranks = new int[15];
        int[] cardRanks = new int[numCards];
        int highCardCount = 0;

        for (int i = 0; i < numCards; i++) {
            int idx = d.getBoardCardIndex(i);
            int suit = idx / 13;
            int rank = idx % 13;
            suits[suit]++;
            ranks[rank]++;
            cardRanks[i] = rank;
            if (rank >= 10) {
                highCardCount++;
            }
        }

        for (int s : suits) {
            if (s >= 4) {
                tex.flushDanger = 3;
            } else if (s == 3) {
                tex.flushDanger = Math.max(tex.flushDanger, 2);
            } else if (s == 2) {
                tex.flushDanger = Math.max(tex.flushDanger, 1);
            }
        }

        int[] sorted = java.util.Arrays.copyOf(cardRanks, numCards);
        java.util.Arrays.sort(sorted);
        int maxRun = 1, currentRun = 1, twoGapCount = 0;

        for (int i = 1; i < sorted.length; i++) {
            int diff = sorted[i] - sorted[i - 1];
            if (diff == 0) {
                continue;
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

        boolean hasAce = ranks[12] > 0;
        boolean hasLow = (ranks[0] > 0 || ranks[1] > 0 || ranks[2] > 0);
        if (hasAce && hasLow) {
            twoGapCount++;
        }

        if (maxRun >= 3) {
            tex.straightDanger = 3;
        } else if (maxRun == 2) {
            tex.straightDanger = twoGapCount > 0 ? 2 : 1;
        } else if (twoGapCount >= 2) {
            tex.straightDanger = 1;
        }

        for (int r : ranks) {
            if (r >= 2) {
                tex.isPaired = true;
                break;
            }
        }
        tex.hasHighCards = (highCardCount >= 2);
        tex.totalScore = tex.flushDanger + tex.straightDanger + (tex.isPaired ? 1 : 0) + (tex.hasHighCards ? 1 : 0);
        return tex;
    }

    private Position determinePosition() {
        if (cachedPosition != Position.UNKNOWN) {
            return cachedPosition;
        }
        cachedPosition = computePosition();
        return cachedPosition;
    }

    private Position computePosition() {
        String myNick = cpuPlayer.getNickname();
        DealerView crupier = dealer();
        String dealerNick = crupier.getDealer_nick();
        if (myNick.equals(dealerNick)) {
            return Position.LATE;
        }
        if (myNick.equals(crupier.getSb_nick()) || myNick.equals(crupier.getBb_nick())) {
            return Position.BLINDS;
        }
        if (myNick.equals(crupier.getUtg_nick())) {
            return Position.EARLY;
        }
        // Cutoff: the active player immediately before the dealer in seating order
        java.util.List<? extends BotPlayerView> jugadores = crupier.getPlayersInSeatingOrder();
        int activos = 0;
        int dealerIdx = -1;
        int myIdx = -1;
        for (BotPlayerView p : jugadores) {
            if (!p.isActivo()) {
                continue;
            }
            String nick = p.getNickname();
            if (nick.equals(dealerNick)) {
                dealerIdx = activos;
            }
            if (nick.equals(myNick)) {
                myIdx = activos;
            }
            activos++;
        }
        if (dealerIdx >= 0 && myIdx >= 0 && activos >= 4) {
            int coIdx = (dealerIdx - 1 + activos) % activos;
            if (myIdx == coIdx) {
                return Position.LATE;
            }
        }
        return Position.MIDDLE;
    }

    private boolean isInPositionPostflop() {
        Position pos = determinePosition();
        return (pos == Position.LATE || pos == Position.MIDDLE);
    }

    /**
     * Difficulty-driven shift applied to heads-up tier-4/5 steal and defense
     * percentages. EASY pulls everything looser (more fish-style play), EXPERT
     * pulls everything tighter (disciplined sharks fold trash). MEDIUM is the
     * neutral baseline. Combined with the profile-specific base rates this
     * gives the per-difficulty VPIP gradient that mixed personality pools
     * alone cannot produce.
     */
    private static int difficultyLoosenessOffset() {
        switch (DIFFICULTY) {
            case EASY:
                return 25;
            case MEDIUM:
                return -16;
            case HARD:
                return -38;
            case EXPERT:
                return -52;
            default:
                return 0;
        }
    }

    private static int clampPct(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 100) {
            return 100;
        }
        return v;
    }

    /**
     * True if the bot holds a high card (J+) that is strictly above the highest
     * board card. As a preflop raiser, the bot's range is concentrated in big
     * cards, so the assumed range-vs-range equity is favourable on these flops.
     */
    private boolean hasRangeAdvantageOverFlop() {
        if (holeCard1 == null || holeCard2 == null) {
            return false;
        }
        int high = Math.max(holeCard1.getRank(), holeCard2.getRank());
        if (high < 9) { // need J (rank 9) or better
            return false;
        }
        DealerView d = dealer();
        int boardSize = d.getBoardSize();
        if (boardSize < 3) {
            return false;
        }
        for (int i = 0; i < boardSize; i++) {
            if ((d.getBoardCardIndex(i) % 13) >= high) {
                return false;
            }
        }
        return true;
    }

    private float potOdds() {
        DealerView d = dealer();
        float toCall = d.getApuesta_actual() - cpuPlayer.getBet();
        return toCall / (d.getBote_total() + toCall);
    }

    private OpponentTracker getPrimaryOpponentStats() {
        BotPlayerView lastAggressor = dealer().getLast_aggressor();
        if (lastAggressor == null || lastAggressor == cpuPlayer) {
            return null;
        }
        return TRACKER_MEMORY.get(lastAggressor.getNickname());
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

    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(Integer carta) {
        return coronaIntegerCard2LokiCard(carta.intValue());
    }

    public static org.alberta.poker.Card coronaCard2LokiCard(Card carta) {
        return new org.alberta.poker.Card(carta.getValorNumerico() - 2, Bot.coronaCardSuit2LokiCardSuit(carta));
    }
}
