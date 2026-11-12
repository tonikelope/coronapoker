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
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.HandStrengthEvaluator.ShowdownOdds;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.bot.eval.PostflopEvaluation;
import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.core.game.GameCardController;
import com.tonikelope.coronapoker.core.game.GameOpponentStats;
import com.tonikelope.coronapoker.core.game.GameOpponentStats.PostflopAction;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import com.tonikelope.coronapoker.crypto.CryptoRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI opponent driving one seat: preflop hand-tier ranges, postflop
 * EV/fold-equity decisions, street plans (bet-bet-bet / bet-check-bet /
 * check-call traps) and a per-difficulty mistake injector, layered over the
 * shared {@link BotEvaluator} (hand strength / draw potential).
 */
public class Bot {

    // Suit order used to index into org.alberta.poker.Card; must match the encoding
    // its Hand/HandEvaluator expect.
    public static final String SUITS = "TDCP";
    public static final int MAX_BET_COUNT = 3;
    // Simulated "thinking" delay (ms) before a bot's action is applied, so bot moves
    // don't render instantly.
    public static final int BOT_THINK_TIME = 1500;

    // Core Alberta Engine Tools (retained as statics so legacy Crupier showdown paths keep
    // working; new bot logic accesses them via the EVALUATOR abstraction below).
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    /**
     * Default evaluator shared by every Bot instance. Computes draw potential
     * and terminal pot share from the same enumeration.
     */
    public static final BotEvaluator EVALUATOR = new MemoizedAlbertaEvaluator();

    // Opponent memory for standalone bots in the offline harness.
    public static final Map<String, OpponentTracker> TRACKER_MEMORY = new ConcurrentHashMap<>();

    // Global difficulty setting
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

    /**
     * Per-opponent behavioural stats (VPIP, PFR, aggression factor) accumulated
     * across hands, used to bias fold-equity and bet/call decisions toward an
     * opponent's observed tendencies.
     */
    public static class OpponentTracker implements GameOpponentStats {

        private int handsPlayed = 0;
        private boolean playedVoluntarilyPreflop;
        private boolean raisedPreflopThisHand;
        private int voluntarilyPutInPot = 0;
        private int preflopRaises = 0;
        private int postFlopBetsAndRaises = 0;
        private int postFlopCalls = 0;
        private int lastVpipHand = -1;
        private int lastPfrHand = -1;
        private int blindDefenses;
        private int blindFolds;
        private final int[][] betsFaced = new int[2][3];
        private final int[][] foldsToBet = new int[2][3];
        private final int[] raisesToBet = new int[3];
        private final int[] openingOpportunities = new int[3];
        private final int[] openingBets = new int[3];
        private final PostflopAction[] postflopActions = {
            PostflopAction.NONE, PostflopAction.NONE, PostflopAction.NONE
        };

        public void recordHandPlayed() {
            handsPlayed++;
            playedVoluntarilyPreflop = false;
            raisedPreflopThisHand = false;
            Arrays.fill(postflopActions, PostflopAction.NONE);
        }

        public void recordVPIP(int handId) {
            playedVoluntarilyPreflop = true;
            if (lastVpipHand != handId) {
                voluntarilyPutInPot++;
                lastVpipHand = handId;
            }
        }

        public void recordPFR(int handId) {
            raisedPreflopThisHand = true;
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

        @Override
        public void recordBlindDefense(boolean folded) {
            blindDefenses++;
            if (folded) {
                blindFolds++;
            }
        }

        @Override
        public double foldToBlindStealRate() {
            return (blindFolds + 4.0) / (blindDefenses + 8);
        }

        @Override
        public void recordPostflopDecision(int street, boolean facingBet, boolean invested, boolean raised,
                boolean folded) {
            int index = street - Crupier.FLOP;
            PostflopAction action = folded ? PostflopAction.FOLD
                    : facingBet ? (raised ? PostflopAction.RAISE : PostflopAction.CALL)
                    : (raised ? PostflopAction.BET : PostflopAction.CHECK);
            if (action.ordinal() > postflopActions[index].ordinal()) {
                postflopActions[index] = action;
            }
            if (facingBet) {
                int wager = invested ? 1 : 0;
                betsFaced[wager][index]++;
                if (folded) {
                    foldsToBet[wager][index]++;
                } else if (raised) {
                    raisesToBet[index]++;
                }
            } else if (!folded) {
                openingOpportunities[index]++;
                if (raised) {
                    openingBets[index]++;
                }
            }
        }

        @Override
        public PostflopAction postflopAction(int street) {
            return postflopActions[street - Crupier.FLOP];
        }

        @Override
        public boolean raisedPreflopThisHand() {
            return raisedPreflopThisHand;
        }

        @Override
        public double raiseToBetRate(int street, double prior) {
            int index = street - Crupier.FLOP;
            return (raisesToBet[index] + 10 * prior) / (betsFaced[0][index] + betsFaced[1][index] + 10);
        }

        @Override
        public double foldToBetRate(int street, boolean invested, double prior) {
            int index = street - Crupier.FLOP;
            int wager = invested ? 1 : 0;
            double folds = foldsToBet[wager][index] + 12 * prior;
            double observations = betsFaced[wager][index] + 12;
            if (street == Crupier.RIVER && !invested) {
                // River observations are sparse. Earlier responses to first
                // bets add limited evidence, leaving direct river reads dominant.
                int earlierBets = betsFaced[wager][0] + betsFaced[wager][1];
                if (earlierBets > 0) {
                    double borrowed = Math.min(6, 0.30 * earlierBets);
                    folds += borrowed * (foldsToBet[wager][0] + foldsToBet[wager][1]) / earlierBets;
                    observations += borrowed;
                }
            }
            return folds / observations;
        }

        @Override
        public double openingBetRate(int street, double prior) {
            int index = street - Crupier.FLOP;
            return (openingBets[index] + 6 * prior) / (openingOpportunities[index] + 6);
        }

        public double getVPIP() {
            return handsPlayed == 0 ? 0 : (double) voluntarilyPutInPot / handsPlayed;
        }

        @Override
        public boolean playedVoluntarilyPreflop() {
            return playedVoluntarilyPreflop;
        }

        @Override
        public double voluntaryPreflopRate() {
            return (voluntarilyPutInPot + 4.8) / (handsPlayed + 12);
        }

        public double getPFR() {
            return handsPlayed == 0 ? 0 : (double) preflopRaises / handsPlayed;
        }

        @Override
        public double preflopRaiseRate(double prior) {
            return (preflopRaises + 24 * prior) / (handsPlayed + 24);
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

        @Override
        public boolean isUnselectiveBettor() {
            int leads = openingBets[0] + openingBets[1] + openingBets[2];
            int opportunities = openingOpportunities[0] + openingOpportunities[1] + openingOpportunities[2];
            return preflopRaises >= 3 && getPFR() > 0.55 && leads >= 2 && 4 * leads >= 3 * opportunities;
        }

        public boolean hasEnoughData() {
            return handsPlayed > 10;
        }

        /**
         * Early read of a passive calling-station shape, available
         * <em>before</em> {@link #hasEnoughData()}: the opponent has called
         * several post-flop bets without ever betting or raising. A thinking
         * player mixes in aggression; a pure station never does. Used to stop
         * bluffing into a player who never folds long before the full
         * {@code isStation()} sample accrues — so the bot does not keep firing
         * −EV bluffs at a fish during the first orbit.
         */
        public boolean looksPassiveStation() {
            return postFlopCalls >= 3 && postFlopBetsAndRaises == 0;
        }
    }

    private volatile BotPlayerView cpuPlayer = null;
    private volatile DealerView dealer = null;
    private volatile BotEvaluator evaluator = EVALUATOR;
    private volatile java.util.Random rng = null;
    private volatile Difficulty perBotDifficulty = null; // null = fall back to static DIFFICULTY
    private volatile Profile forcedProfile = null;
    private volatile Profile baseProfile;
    private volatile Profile currentProfile;
    private volatile Skill skillLevel;

    private volatile org.alberta.poker.Card holeCard1 = null;
    private volatile org.alberta.poker.Card holeCard2 = null;
    private volatile boolean slowPlay = false;
    private boolean cBetConsidered;

    private volatile double previousStrength = -1.0;
    private volatile int previousStreet = -1;
    private volatile boolean scareCardDetected = false;
    private volatile double lastEffectiveStrength = 0.5;
    // Bet size computed during computeRawDecision (for the EV of BET) and reused
    // verbatim by the dealer when it executes the bet. getBetSize() embeds RNG
    // (sizing jitter, recreational sizing), so recomputing it for the wire would
    // make the executed amount differ from the one the decision was evaluated on.
    private volatile double lastBetSize = 0;

    private volatile Position cachedPosition = Position.UNKNOWN;
    private volatile BoardTexture cachedTexture = null;
    private volatile int cachedTextureBoardSize = -1;
    private int[] cachedEvaluationBoard;
    private int cachedEvaluationOpponents;
    private PostflopEvaluation cachedEvaluation;
    private OpponentRangeModel cachedOpponentRangeModel;
    private double[][] cachedRangeWeights;
    private ShowdownOdds cachedRangeOdds;

    // Advanced state
    private volatile boolean floatPlay = false;
    private volatile int streetPlan = PLAN_NONE;
    private volatile int streetPlanStartStreet = -1;
    private volatile int consecutiveLosses = 0;
    private volatile boolean onTilt = false;
    private volatile double previousPpot = 0.0;
    private volatile boolean aggressiveLine = false;

    private static final Logger LOGGER = Logger.getLogger(Bot.class.getName());

    /**
     * Attaches this AI to any {@link BotPlayerView} seat and rolls its
     * skill/style personality.
     */
    public Bot(BotPlayerView player) {
        this(player, null);
    }

    /**
     * Creates a replayable bot whose personality and later decisions consume
     * the same injected random stream from the very first roll.
     */
    public Bot(BotPlayerView player, java.util.Random rng) {
        this.cpuPlayer = player;
        this.rng = rng;
        assignPersonality();
    }

    /**
     * Inject the dealer view and evaluator used by this bot. Tests and the
     * offline harness call this to bypass {@code GameFrame.getInstance()}.
     */
    public void setContext(DealerView dealer, BotEvaluator evaluator) {
        this.dealer = dealer;
        if (evaluator != null && evaluator != this.evaluator) {
            this.evaluator = evaluator;
            cachedEvaluationBoard = null;
        }
    }

    /**
     * Inject a seeded RNG for deterministic replay; passing null restores the
     * shared cryptographic random source.
     */
    public void setRng(java.util.Random rng) {
        this.rng = rng;
    }

    /**
     * Override the global {@link #DIFFICULTY} for this bot specifically. Used
     * by mixed-matchup tests (e.g. HARD vs EASY in the same hand). Re-rolls the
     * bot's personality so the new skill distribution applies. Passing null
     * returns to the global static fallback.
     */
    public void setDifficulty(Difficulty d) {
        this.perBotDifficulty = d;
        assignPersonality();
    }

    /** Pins this bot to one behavioural style; null restores random style. */
    public void setProfile(Profile profile) {
        forcedProfile = profile;
        assignPersonality();
    }

    /**
     * Applies both per-seat AI overrides with a single personality roll.
     *
     * <p>This is the normal lobby path. Keeping the two assignments atomic
     * avoids consuming an otherwise invisible extra RNG roll when a custom
     * bot defines both its difficulty and its behavioural profile.</p>
     */
    public void configure(Difficulty difficulty, Profile profile) {
        perBotDifficulty = difficulty;
        forcedProfile = profile;
        assignPersonality();
    }

    private Difficulty effectiveDifficulty() {
        Difficulty d = perBotDifficulty;
        return d != null ? d : DIFFICULTY;
    }

    private int randInt(int bound) {
        java.util.Random r = rng;
        return r != null ? r.nextInt(bound) : CryptoRandom.nextInt(bound);
    }

    private float randFloat() {
        java.util.Random r = rng;
        return r != null ? r.nextFloat() : (float) CryptoRandom.nextDouble();
    }

    private double randDouble() {
        java.util.Random r = rng;
        return r != null ? r.nextDouble() : CryptoRandom.nextDouble();
    }

    protected DealerView dealer() {
        DealerView d = dealer;
        if (d == null) {
            throw new IllegalStateException("Bot has no bound canonical dealer");
        }
        return d;
    }

    protected BotPlayerView player() {
        return cpuPlayer;
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
     * Verbose logger for debugging bot decisions.
     *
     * The {@code isLoggable} gate skips the {@code String.format} (pattern
     * parsing + substitution) when INFO is off, the normal production case.
     * Called 5-15 times per decision x N bots x hands, so without the gate the
     * cost showed up in profiling even with the file handler disabled.
     *
     * Note: the {@code message} string concatenation at each call site (e.g.
     * {@code logVerbose("Overcard Penalty: Pair crushed by " + overcards + " cards.")})
     * still runs regardless. Avoiding that too would mean migrating all call
     * sites to {@code Supplier<String>} — deferred.
     */
    private void logVerbose(String message) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
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
        //   HARD:     0 rec  / 35 reg / 65 shark   ("professional table")
        int recThreshold, regThreshold;
        switch (effectiveDifficulty()) {
            case EASY:
                recThreshold = 60;
                regThreshold = 92;
                break;
            case HARD:
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
            switch (effectiveDifficulty()) {
                case EASY:
                    stationCut = 75;
                    lagCut = 87;
                    tagCut = 96; // 75/12/9/4 (less LAG to drop PFR)
                    break;
                case MEDIUM:
                    stationCut = 55;
                    lagCut = 78;
                    tagCut = 92; // 55/23/14/8
                    break;
                default: // HARD (rec is tiny anyway)
                    stationCut = 45;
                    lagCut = 73;
                    tagCut = 90; // 45/28/17/10
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
        if (forcedProfile != null) baseProfile = forcedProfile;
        currentProfile = baseProfile;
        logVerbose("Personality assigned. Base: " + baseProfile.name());
    }

    private void adjustProfileElasticity() {
        DealerView dealer = dealer();
        float stack = (float) cpuPlayer.getStack();
        float blindsCost = (float) (dealer.getCiega_grande() + dealer.getCiega_pequeña());
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

    /**
     * Resets all per-hand state (street plan, caches, slowplay/tilt rolls) at
     * the start of a new hand and re-derives the current profile from stack
     * depth.
     */
    public void resetBot() {
        holeCard1 = new org.alberta.poker.Card(cpuPlayer.getHoleCard1Index());
        holeCard2 = new org.alberta.poker.Card(cpuPlayer.getHoleCard2Index());

        if (skillLevel == Skill.RECREATIONAL && consecutiveLosses >= 2) {
            onTilt = randInt(100) < (30 + consecutiveLosses * 10);
            if (onTilt) {
                logVerbose("Recreational bot has gone on TILT.");
            }
        } else {
            onTilt = false;
        }
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

        floatPlay = false;
        cBetConsidered = false;
        previousStrength = -1.0;
        previousStreet = Crupier.PREFLOP;
        scareCardDetected = false;
        streetPlan = PLAN_NONE;
        streetPlanStartStreet = -1;
        previousPpot = 0.0;
        aggressiveLine = false;
        lastEffectiveStrength = 0.5;
        lastBetSize = 0f;
        cachedPosition = Position.UNKNOWN;
        cachedTexture = null;
        cachedTextureBoardSize = -1;
        cachedEvaluationBoard = null;
        cachedEvaluation = null;
    }

    /**
     * Updates the recreational tilt tracker after a hand resolves: a win clears
     * the loss streak and any tilt, a loss extends the streak.
     *
     * @param won whether this bot won the hand
     */
    public void recordHandResult(boolean won) {
        if (won) {
            consecutiveLosses = 0;
            onTilt = false;
        } else {
            consecutiveLosses++;
        }
    }

    /**
     * @return the bet size computed during the last decision, NOT a freshly
     * recomputed one: a new call would draw new RNG and bet a different amount
     * than the one the EV was evaluated on
     */
    public double getBetSize() {
        return lastBetSize;
    }

    /**
     * Computes a bet/raise size for the current street: preflop open/3-bet
     * sizing, or postflop sizing driven by board texture and pot fraction, then
     * rounded to a legal small-blind multiple of the dealer's current stakes.
     *
     * @param effectiveStrength this bot's current hand-strength estimate (0-1)
     * @return the target bet size, legal and aligned to the current small blind
     */
    public double getBetSize(double effectiveStrength) {
        DealerView dealer = dealer();
        // Boundary: the dealer money is double; the bot's sizing math is float
        // (ratios/RNG), so narrow here and let the float result widen back to the
        // double money output on return.
        float pot = (float) dealer.getBote_total();
        float currentBet = (float) dealer.getApuesta_actual();
        float minRaise = (float) (MoneyMath.compare(0, dealer.getUltimo_raise()) < 0 ? dealer.getUltimo_raise() : dealer.getCiega_grande());
        float bb = (float) dealer.getCiega_grande();
        float targetBet;

        if (dealer.getStreet() == Crupier.PREFLOP) {
            if (dealer.getConta_bet() > 0) {
                targetBet = Math.min(minRaise * 3.0f, (pot > 0 ? pot * 1.5f : minRaise * 4.0f));
            } else {
                int limpers = dealer.getLimpersCount();
                boolean smallSteal = effectiveDifficulty() == Difficulty.HARD && limpers == 0
                        && dealer.getStraddleUtgNick() == null && determinePosition() == Position.LATE
                        && !cpuPlayer.getNickname().equals(dealer.getSb_nick());
                targetBet = ((smallSteal ? 2.0f : 2.5f) + limpers) * bb;
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
        // Round to the minimum chip = the dealer's CURRENT SB, not
        // GameFrame.CIEGA_PEQUEÑA (the global initial SB, which doesn't
        // reflect doblarCiegas or recovery).
        float sb = (float) dealer.getCiega_pequeña();
        if (sb <= 0f) {
            sb = (float) dealer.getInitialSmallBlind();
        }
        targetBet = (float) (Math.ceil(MoneyMath.clean(targetBet) / sb) * sb);

        if (MoneyMath.compare(currentBet, 0f) == 0 || (dealer.getStreet() == Crupier.PREFLOP && MoneyMath.compare(currentBet, bb) == 0)) {
            // Opening bet: bb and targetBet are already sb multiples (in the default
            // ladder bb = 2*sb, and targetBet is aligned by the Math.ceil above), so
            // the final max keeps the alignment.
            return Math.max(bb, targetBet);
        } else {
            // Raise: the final bet is composed as currentBet + delta. If currentBet is
            // fractional relative to the CURRENT sb — typical of a prior all-in with a
            // residual stack that isn't a multiple of the new sb after doblarCiegas or
            // after recovery with doubled blinds — the sum won't be aligned even though
            // targetBet and minRaise were. Realign the total to the next sb multiple so
            // the bot's bet is ALWAYS legal. The rounding up here is already covered by
            // the caller's stack > 75% check, which converts the bet to ALLIN if the
            // raise ends up close to the full stack.
            float raw_result = Math.max(currentBet + minRaise, currentBet + targetBet);
            return (float) (Math.ceil(MoneyMath.clean(raw_result) / sb) * sb);
        }
    }

    /**
     * Computes this bot's action for the current decision point: the
     * EV/fold-equity-driven decision, possibly downgraded to a recognisable
     * recreational mistake per {@link #mistakeRateForDifficulty()}.
     *
     * @param opponentsCount number of other players still live in the hand
     * @return one of {@link Player#BET}, {@link Player#CHECK} (covers check and
     * call) or {@link Player#FOLD}
     */
    public int calculateBotDecision(int opponentsCount) {
        int handTier = dealer().getStreet() == Crupier.PREFLOP ? preflopHandTier() : 0;
        int decision = computeRawDecision(opponentsCount, handTier);
        double mistakeRate = mistakeRateForDifficulty();
        if (mistakeRate > 0.0 && randDouble() < mistakeRate) {
            int corrupted = injectRecreationalMistake(decision, handTier);
            if (corrupted != decision) {
                logVerbose("MISTAKE injection (" + effectiveDifficulty() + "): "
                        + decisionName(decision) + " -> " + decisionName(corrupted));
                decision = corrupted;
            }
        }
        if (decision == GamePlayerController.BET && dealer().getStreet() >= Crupier.FLOP) {
            aggressiveLine = true;
        }
        return decision;
    }

    /**
     * Per-difficulty probability of replacing the bot's optimal-ish decision
     * with a recognisable recreational poker mistake. HARD (the top level)
     * plays its baseline cleanly. Lower difficulties commit recognisable leaks
     * (sticky call, hero fold, missed value bet, spewy preflop call) at
     * increasing frequencies — the Stockfish pattern that guarantees HARD &gt;
     * MEDIUM &gt; EASY in bb/100 by construction. The three rates are spaced
     * wide (0 / 22 / 45) so each level is clearly distinguishable to a human
     * within a single session.
     */
    private double mistakeRateForDifficulty() {
        switch (effectiveDifficulty()) {
            case HARD:
                return 0.00;
            case MEDIUM:
                return 0.22;
            case EASY:
                return 0.45;
            default:
                return 0.0;
        }
    }

    private static String decisionName(int d) {
        switch (d) {
            case GamePlayerController.BET:
                return "BET";
            case GamePlayerController.CHECK:
                return "CHECK/CALL";
            case GamePlayerController.FOLD:
                return "FOLD";
            default:
                return "?";
        }
    }

    /**
     * Replace the bot's planned decision with a recreational leak when the
     * current spot fits one. Mistakes are tried in cascade: the first
     * applicable one fires. All downgrade decisions toward passivity or
     * surrender; none inserts a new BET that would inflate the tracker's
     * aggression factor.
     */
    private int injectRecreationalMistake(int planned, int handTier) {
        double toCall = callCost();
        double pot = contestablePot(toCall);
        if (handTier > 0) {
            // Speculative hands can make a loose call. Pure trash only does
            // so cheaply, and a free check can never turn into a fold.
            double priceLimit = handTier == 5 ? 0.65 : 1.5;
            if (planned == GamePlayerController.FOLD && handTier >= 3
                    && toCall > 0 && toCall < pot * priceLimit) {
                return GamePlayerController.CHECK;
            }
            return planned;
        }
        double strength = lastEffectiveStrength;
        if (toCall > 0 && dealer().getStreet() == Crupier.RIVER) {
            ShowdownOdds odds = cachedEvaluation.currentOdds();
            if (odds.win() + odds.tie() >= 1 - 1e-12) {
                return planned;
            }
        }

        // 1. Sticky calldown: weak made hand calls bet instead of folding.
        if (planned == GamePlayerController.FOLD && toCall > 0f
                && strength > 0.10 && strength < 0.55
                && toCall < pot * 1.5f) {
            return GamePlayerController.CHECK;
        }

        // 2. A recreational player can surrender a strong but beatable hand.
        if (planned == GamePlayerController.CHECK && toCall > 0 && strength > 0.62) {
            return GamePlayerController.FOLD;
        }
        if (planned == GamePlayerController.BET && toCall > 0 && strength > 0.70) {
            return GamePlayerController.FOLD;
        }

        // 3. Missed value bet: strong made hand passes on the chance to
        // grow the pot when checked to.
        if (planned == GamePlayerController.BET && toCall <= 0 && strength > 0.62) {
            return GamePlayerController.CHECK;
        }

        return planned;
    }

    private int computeRawDecision(int opponentsCount, int handTier) {
        DealerView dealer = dealer();
        int street = dealer.getStreet();
        int activePlayers = opponentsCount + 1;
        int betCount = dealer.getConta_bet();

        if (street == Crupier.PREFLOP) {
            if (effectiveDifficulty() == Difficulty.HARD && opponentsCount == 1) {
                int committedDecision = closedPreflopDecision(dealer, betCount);
                if (committedDecision != GamePlayerController.NODEC) {
                    return committedDecision;
                }
            }
            int decision = calculatePreflopAction(betCount, activePlayers, handTier);
            if (decision == GamePlayerController.FOLD) {
                if (callCost() == 0) {
                    return GamePlayerController.CHECK;
                }
                if (betCount == 0 && opponentsCount == 1 && profitableBlindSteal()) {
                    return GamePlayerController.BET;
                }
            }
            if (decision == GamePlayerController.BET) {
                lastBetSize = finalBetTarget(getBetSize(0));
                if (!canRaiseTo(lastBetSize)) {
                    return GamePlayerController.CHECK;
                }
            }
            return decision;
        }

        int[] board = currentBoard();
        int hole1 = holeCard1.getIndex();
        int hole2 = holeCard2.getIndex();
        int contenders = 0;
        for (BotPlayerView seat : dealer.getPlayersInSeatingOrder()) {
            if (isOpponentInHand(seat)) {
                contenders++;
            }
        }
        int maxOpponents = Math.max(opponentsCount, contenders);
        if (!Arrays.equals(cachedEvaluationBoard, board) || cachedEvaluationOpponents < maxOpponents) {
            cachedEvaluation = evaluator.evaluatePostflop(hole1, hole2, board, maxOpponents);
            cachedEvaluationBoard = board;
            cachedEvaluationOpponents = maxOpponents;
            cachedRangeWeights = null;
            cachedOpponentRangeModel = null;
        }
        PostflopEvaluation evaluation = cachedEvaluation;
        ShowdownOdds odds = evaluation.currentOdds();
        double strength = odds.equity(opponentsCount);
        double ppot = evaluation.potential().ppot();
        double npot = evaluation.potential().npot();
        double rawEquity = evaluation.equity(opponentsCount);
        double effectiveStrength = rawEquity;

        boolean pocketPair = holeCard1.getRank() == holeCard2.getRank();
        boolean weakKicker = !pocketPair && strength > 0.50 && strength < 0.80
                && Math.min(holeCard1.getRank(), holeCard2.getRank()) < 6;
        boolean onePair = false;
        if (pocketPair || weakKicker) {
            int[] cards = Arrays.copyOf(board, board.length + 2);
            cards[board.length] = hole1;
            cards[board.length + 1] = hole2;
            onePair = evaluator.isOnePair(cards);
        }
        boolean unimprovedPocketPair = pocketPair && onePair;
        if (unimprovedPocketPair) {
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

        if (weakKicker && onePair) {
            effectiveStrength -= 0.12;
            logVerbose("Weak Kicker Penalty applied.");
        }
        double equityAdjustment = effectiveStrength - rawEquity;
        effectiveStrength = Math.max(0.10, effectiveStrength);

        if (street != previousStreet) {
            if (previousStrength != -1.0) {
                scareCardDetected = ((effectiveStrength - previousStrength) < -SCARE_CARD_DROP);
            }
            if (street < Crupier.RIVER) {
                previousPpot = ppot;
            }
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
        double callCost = callCost();
        double callPot = contestablePot(callCost);
        float spr = (float) (cpuPlayer.getStack() / (callPot > 0 ? callPot : 1.0));

        boolean potCommitted = false;
        if (callCost > 0) {
            float commitAdjust = (skillLevel == Skill.RECREATIONAL) ? -0.05f : 0f;
            if ((spr <= 2.0f && effectiveStrength >= (POT_COMMIT_LOW_SPR_EQ + commitAdjust))
                    || (spr <= 4.0f && effectiveStrength >= (POT_COMMIT_HIGH_SPR_EQ + commitAdjust))) {
                potCommitted = true;
                logVerbose("Bot considers itself POT COMMITTED.");
            }
        }

        BotPlayerView bettor = dealer.getLastWagerAggressor();
        GameOpponentStats targetStats = bettor != null && isOpponentInHand(bettor) ? opponentStats(bettor) : null;
        OpponentRangeModel.HoldingRange observedBettorRange = callCost > 0
                ? observedOpponentRange(board, bettor, targetStats) : null;
        ShowdownOdds observedBettorOdds = observedBettorRange == null ? null : observedBettorRange.odds();
        double difficultyNoise = (effectiveDifficulty() == Difficulty.EASY && skillLevel != Skill.RECREATIONAL) ? (randDouble() * 0.10 - 0.05) : 0.0;

        if (skillLevel != Skill.RECREATIONAL) {
            if (betCount > 1) {
                equityAdjustment -= 0.10;
            }
            if (scareCardDetected && currentProfile != Profile.LAG) {
                equityAdjustment -= 0.12;
            }
            if (!isInPositionPostflop()) {
                equityAdjustment -= 0.03;
            }

            if (betCount > 0 && targetStats != null && targetStats.hasEnoughData()) {
                if (targetStats.isNit()) {
                    equityAdjustment -= 0.18;
                } else if (targetStats.isManiac()) {
                    equityAdjustment += 0.12;
                } else if (targetStats.isStation()) {
                    equityAdjustment -= 0.03;
                }
            }
            if (observedBettorOdds == null && effectiveDifficulty() == Difficulty.HARD
                    && opponentsCount == 1 && betCount == 1
                    && street >= Crupier.TURN && targetStats != null) {
                double openingRate = targetStats.openingBetRate(street, 1);
                double rangePenalty = odds.equity(1) - equityAgainstStrongestHands(odds, openingRate);
                equityAdjustment -= (street == Crupier.RIVER ? 0.65 : 0.40) * rangePenalty;
            }
        } else {
            if (onTilt) {
                equityAdjustment += 0.10;
            }
        }

        double[] equity = new double[maxOpponents + 1];
        equity[0] = 1;
        // With an unbeatable hand we can only win or chop. Opponent reads
        // and position cannot turn either outcome into a loss at showdown.
        boolean certainRiver = street == Crupier.RIVER
                && (odds.win() + odds.tie() >= 1 - 1e-12 || odds.win() + odds.tie() == 0);
        for (int opponents = 1; opponents <= maxOpponents; opponents++) {
            double estimate = evaluation.equity(opponents);
            equity[opponents] = certainRiver ? estimate : clampEquity(estimate + equityAdjustment + difficultyNoise);
        }
        RangeEquity range = null;
        if (!certainRiver) {
            if (street == Crupier.RIVER && maxOpponents == 2) {
                range = riverTwoOpponentEquity(board, equity, evaluation, bettor, observedBettorRange);
            } else if (maxOpponents >= 4 || (street == Crupier.RIVER && maxOpponents == 3)) {
                range = jointOpponentEquity(board, equity, evaluation, bettor, observedBettorRange);
            }
            if (range == null && callCost > 0) {
                range = bettorEquity(bettor, opponentsCount, board, equity, targetStats, odds, observedBettorRange,
                        street, betCount);
            } else if (range == null && street == Crupier.RIVER && equity.length == 2) {
                range = riverOpponentEquity(board, equity, odds);
            }
        }
        boolean safeDraw = npot < 0.15;
        double passiveEv = evaluatePotAction(callCost, 0, equity, range, boardTexture, betCount, street, pot).ev();
        double winProb = range == null ? equity[opponentsCount] : range.shares()[opponentsCount];
        double evCall = passiveEv;
        if (callCost > 0 && street < Crupier.RIVER && ppot > 0.15 && strength < 0.50 && safeDraw) {
            evCall += winProb * effectiveStackBehind() * Math.min(0.25, ppot * 0.5);
        }

        RaiseEvaluation raise = selectPostflopRaise(effectiveStrength, opponentsCount, equity, range,
                boardTexture, betCount, street, pot);

        int decision;
        if (callCost == 0) {
            decision = decisionWhenCheckedTo(effectiveStrength, equity, range, passiveEv, raise, ppot, npot, pot,
                    street, activePlayers, boardTexture);
        } else {
            decision = decisionWhenFacingBet(effectiveStrength, winProb, evCall, raise,
                    ppot, callPot, callCost, street, betCount, activePlayers, potCommitted,
                    boardTexture, targetStats, unimprovedPocketPair, certainRiver);
        }

        lastEffectiveStrength = effectiveStrength;
        return decision;
    }

    private record RangeEquity(String opponent, ShowdownOdds odds, double[] shares,
            String otherOpponent, double otherShare, JointEquity joint) {

        private RangeEquity(String opponent, ShowdownOdds odds, double[] shares) {
            this(opponent, odds, shares, null, 0, null);
        }

        private RangeEquity(String opponent, ShowdownOdds odds, double[] shares, String otherOpponent, double otherShare) {
            this(opponent, odds, shares, otherOpponent, otherShare, null);
        }
    }

    private record JointEquity(IntToDoubleFunction showdown, double[] adjusted, PostflopEvaluation uniform) {
        double share(int mask) {
            int count = Integer.bitCount(mask);
            double expected = showdown.applyAsDouble(mask);
            return Double.isFinite(expected) ? blendRangeEquity(adjusted[count], uniform.equity(count), expected)
                    : adjusted[count];
        }
    }

    private RangeEquity jointOpponentEquity(int[] board, double[] equity, PostflopEvaluation evaluation,
            BotPlayerView bettor, OpponentRangeModel.HoldingRange bettorRange) {
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL) {
            return null;
        }
        OpponentRangeModel.HoldingRange[] holdings = new OpponentRangeModel.HoldingRange[equity.length - 1];
        int seats = 0;
        int observed = 0;
        for (BotPlayerView opponent : dealer().getPlayersInSeatingOrder()) {
            if (!isOpponentInHand(opponent)) {
                continue;
            }
            if (seats == holdings.length) {
                return null;
            }
            var range = opponent == bettor && bettorRange != null ? bettorRange
                    : observedOpponentRange(board, opponent, opponentStats(opponent));
            holdings[seats++] = range;
            if (range != null) {
                observed++;
            }
        }
        if (observed < 2 || seats != holdings.length) {
            return null;
        }
        IntToDoubleFunction showdown;
        if (seats == 3) {
            showdown = cachedOpponentRangeModel.threeSeatShowdown(holdings)::share;
        } else {
            showdown = cachedOpponentRangeModel.jointShowdown(holdings)::share;
        }
        if (!Double.isFinite(showdown.applyAsDouble((1 << seats) - 1))) {
            return null;
        }
        JointEquity model = new JointEquity(showdown, equity, evaluation);
        double[] shares = equity.clone();
        shares[seats] = model.share((1 << seats) - 1);
        return new RangeEquity(null, null, shares, null, 0, model);
    }

    private ShowdownOdds observedOpponentOdds(int[] board, BotPlayerView opponent, GameOpponentStats stats) {
        OpponentRangeModel.HoldingRange range = observedOpponentRange(board, opponent, stats);
        return range == null ? null : range.odds();
    }

    private OpponentRangeModel.HoldingRange observedOpponentRange(int[] board, BotPlayerView opponent,
            GameOpponentStats stats) {
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL
                || stats == null || stats.isManiac() || stats.isUnselectiveBettor()
                || opponent == null || !isOpponentInHand(opponent)) {
            return null;
        }
        if (cachedOpponentRangeModel == null) {
            cachedOpponentRangeModel = new OpponentRangeModel(evaluator, holeCard1.getIndex(), holeCard2.getIndex(), board);
        }
        boolean raisedPreflop = stats.raisedPreflopThisHand()
                || opponent.getNickname().equals(dealer().getPreflopAggressorNick());
        return cachedOpponentRangeModel.range(stats, raisedPreflop);
    }

    private RangeEquity riverTwoOpponentEquity(int[] board, double[] equity, PostflopEvaluation evaluation,
            BotPlayerView bettor, OpponentRangeModel.HoldingRange bettorRange) {
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL) {
            return null;
        }
        String firstOpponent = null;
        OpponentRangeModel.HoldingRange firstRange = null;
        for (BotPlayerView opponent : dealer().getPlayersInSeatingOrder()) {
            if (!isOpponentInHand(opponent)) {
                continue;
            }
            OpponentRangeModel.HoldingRange current = bettorRange != null && opponent == bettor ? bettorRange
                    : observedOpponentRange(board, opponent, opponentStats(opponent));
            if (current == null) {
                return null;
            }
            if (firstRange == null) {
                firstOpponent = opponent.getNickname();
                firstRange = current;
                continue;
            }
            double firstShare = blendRangeEquity(equity[1], evaluation.equity(1), firstRange.odds().equity(1));
            double otherShare = blendRangeEquity(equity[1], evaluation.equity(1), current.odds().equity(1));
            double weighted = cachedOpponentRangeModel.twoOpponentShare(firstRange, current);
            double combined = Math.min(Math.min(firstShare, otherShare),
                    blendRangeEquity(equity[2], evaluation.equity(2), weighted));
            return new RangeEquity(firstOpponent, firstRange.odds(), new double[]{1, firstShare, combined},
                    opponent.getNickname(), otherShare);
        }
        return null;
    }

    private static double blendRangeEquity(double adjusted, double uniform, double weighted) {
        return clampEquity(adjusted + 0.8 * (weighted - uniform));
    }

    private RangeEquity bettorEquity(BotPlayerView aggressor, int opponents, int[] board, double[] equity,
            GameOpponentStats stats, ShowdownOdds odds, OpponentRangeModel.HoldingRange observedRange,
            int street, int betCount) {
        ShowdownOdds observedOdds = observedRange == null ? null : observedRange.odds();
        DealerView table = dealer();
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL
                || (opponents > 3 && observedOdds == null)
                || stats == null || stats.isManiac() || aggressor == null || !isOpponentInHand(aggressor)) {
            return null;
        }
        boolean raisedPreflop = aggressor.getNickname().equals(table.getPreflopAggressorNick());
        ShowdownOdds rangeOdds = observedOdds == null ? odds : observedOdds;
        if (observedOdds == null && (raisedPreflop || stats.playedVoluntarilyPreflop())) {
            rangeOdds = preflopRangeOdds(board, stats, raisedPreflop);
        }
        double adjustment = rangeOdds.equity(1) - odds.equity(1);
        if (observedOdds == null && opponents > 1 && street >= Crupier.TURN && betCount == 1) {
            double openingRate = stats.openingBetRate(street, 0.73);
            double rangePenalty = odds.equity(1) - equityAgainstStrongestHands(odds, openingRate);
            adjustment -= (street == Crupier.RIVER ? 0.65 : 0.55) * rangePenalty;
        }
        if (opponents == 1 && street < Crupier.RIVER && currentProfile == Profile.STATION && observedRange != null) {
            // A passive line realizes its draws and improvements on the remaining board.
            double terminalShare = cachedOpponentRangeModel.jointShowdown(
                    new OpponentRangeModel.HoldingRange[]{observedRange}).share(1);
            if (Double.isFinite(terminalShare)) {
                adjustment = terminalShare - cachedEvaluation.equity(1);
            }
        }
        if (Math.abs(adjustment) <= 1e-12) {
            return null;
        }
        double[] shares = equity.clone();
        for (int i = 1; i <= opponents; i++) {
            double weight = 0.80 / (1 + 0.45 * (i - 1));
            shares[i] = Math.min(shares[i - 1], clampEquity(shares[i] + weight * adjustment));
        }
        return new RangeEquity(aggressor.getNickname(), rangeOdds, shares);
    }

    private RangeEquity riverOpponentEquity(int[] board, double[] equity, ShowdownOdds odds) {
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL) {
            return null;
        }
        for (BotPlayerView opponent : dealer().getPlayersInSeatingOrder()) {
            if (!isOpponentInHand(opponent)) {
                continue;
            }
            GameOpponentStats stats = opponentStats(opponent);
            if (stats == null || stats.isManiac()) {
                return null;
            }
            ShowdownOdds rangeOdds = observedOpponentOdds(board, opponent, stats);
            if (rangeOdds == null) {
                if (!stats.playedVoluntarilyPreflop()) {
                    return null;
                }
                boolean raisedPreflop = opponent.getNickname().equals(dealer().getPreflopAggressorNick());
                rangeOdds = preflopRangeOdds(board, stats, raisedPreflop);
            }
            double adjustment = rangeOdds.equity(1) - odds.equity(1);
            double share = clampEquity(equity[1] + 0.80 * adjustment);
            return new RangeEquity(opponent.getNickname(), rangeOdds, new double[]{1, share});
        }
        return null;
    }

    private ShowdownOdds preflopRangeOdds(int[] board, GameOpponentStats stats, boolean raisedPreflop) {
        double width = Math.min(0.85, (raisedPreflop ? 0.04 : 0.14) + stats.voluntaryPreflopRate());
        double[][] weights = StartingHandRanges.weights(width);
        if (cachedRangeWeights != weights) {
            cachedRangeOdds = evaluator.showdownOddsVsRange(holeCard1.getIndex(), holeCard2.getIndex(), board, weights);
            cachedRangeWeights = weights;
        }
        return cachedRangeOdds;
    }

    private boolean profitableBlindSteal() {
        DealerView table = dealer();
        String bigBlind = table.getBb_nick();
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL
                || currentProfile == Profile.STATION || table.getLimpersCount() != 0
                || !cpuPlayer.getNickname().equals(table.getSb_nick())
                || bigBlind == null || table.getStraddleUtgNick() != null
                || effectiveStackBehind() < 10 * table.getCiega_grande()) {
            return false;
        }
        GameOpponentStats stats = table.getOpponentStats(bigBlind);
        if (stats == null) {
            stats = TRACKER_MEMORY.get(bigBlind);
        }
        double fold = stats == null ? 0 : stats.foldToBlindStealRate();
        if (fold <= 0.60) {
            return false;
        }
        double target = finalBetTarget(getBetSize(0));
        double risk = target - cpuPlayer.getBet();
        if (fold * table.getBote_total() - (1 - fold) * risk <= 0.10 * table.getCiega_grande()
                || !canRaiseTo(target)) {
            return false;
        }
        lastBetSize = target;
        logVerbose("Blind steal against an opponent who often folds to opens.");
        return true;
    }

    private int decisionWhenCheckedTo(double effectiveStrength, double[] equity, RangeEquity range, double checkEv,
            RaiseEvaluation raise, double ppot, double npot, double pot, int street,
            int activePlayers, BoardTexture boardTexture) {
        boolean valueBetPays = improvesEv(raise.ev(), checkEv);
        if (activePlayers == 2 && profitableSmallProbe(checkEv, pot, street)) {
            return GamePlayerController.BET;
        }

        if (floatPlay && street == Crupier.TURN) {
            floatPlay = false;
            if (activePlayers == 2 && isInPositionPostflop()
                    && profitableBluffSize(raise, equity, range, checkEv, pot, boardTexture, street)) {
                logVerbose("Executing Float Bluff follow-through on Turn.");
                return GamePlayerController.BET;
            }
        }

        if (slowPlay && effectiveStrength >= STRENGTH_NUT_TRAP && street < Crupier.RIVER) {
            if (boardTexture.totalScore >= 3) {
                logVerbose("Aborting slowplay due to wet board texture.");
                slowPlay = false;
            } else {
                logVerbose("Executing slowplay trap (Check).");
                return GamePlayerController.CHECK;
            }
        }

        if (street == Crupier.RIVER) {
            if (valueBetPays && effectiveStrength >= STRENGTH_RIVER_VALUE) {
                logVerbose("River Value Bet.");
                return GamePlayerController.BET;
            }
            if (valueBetPays && skillLevel == Skill.SHARK && effectiveStrength >= STRENGTH_VALUE_BET
                    && boardTexture.totalScore <= 2 && isInPositionPostflop()
                    && activePlayers <= 2 && randInt(100) < 55) {
                logVerbose("River thin value bet (small).");
                return GamePlayerController.BET;
            }
            boolean hard = effectiveDifficulty() == Difficulty.HARD;
            boolean missedDraw = previousPpot > 0.18;
            double showdownStrength = range == null ? effectiveStrength : Math.min(effectiveStrength, range.shares()[1]);
            if (skillLevel != Skill.RECREATIONAL
                    && activePlayers == 2 && showdownStrength < (hard ? 0.42 : 0.35)) {
                boolean blocker = hard && hasRiverBluffBlocker();
                boolean credible = boardTexture.totalScore <= 3 || blocker
                        || (hard && (missedDraw || showdownStrength < 0.13) && isInPositionPostflop());
                int chance = riverBluffChance() + (missedDraw || aggressiveLine ? 12 : 0) + (blocker ? 18 : 0);
                if (credible && randInt(100) < chance
                        && profitableBluffSize(raise, equity, range, checkEv, pot, boardTexture, street)) {
                    logVerbose("River polarized bluff.");
                    return GamePlayerController.BET;
                }
            }
            return GamePlayerController.CHECK;
        }

        if (street == Crupier.FLOP && !cBetConsidered && currentProfile != Profile.STATION
                && cpuPlayer.getNickname().equals(dealer().getPreflopAggressorNick())) {
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
                if (effectiveDifficulty() == Difficulty.HARD && hasRangeAdvantageOverFlop()) {
                    cbetChance += 15;
                    logVerbose("Range advantage detected on flop (high-card top-down).");
                }
            }

            cBetConsidered = true;
            if (randInt(100) < cbetChance && (effectiveStrength >= STRENGTH_VALUE_BET && valueBetPays
                    || profitableBluffSize(raise, equity, range, checkEv, pot, boardTexture, street))) {
                logVerbose("Executing C-Bet.");
                return GamePlayerController.BET;
            }
        }

        // Semi-bluff with a strong draw. The PPot threshold is street-aware: on
        // the turn only one card is to come, so a flush/straight draw's one-card
        // PPot (~0.17-0.20) sits far below the flop's two-card PPot. The old flat
        // 0.30 gate meant the bot never semi-bluffed the turn — it only barrelled
        // made hands — which made its turn aggression readable.
        double semiBluffPpot = (street == Crupier.TURN) ? 0.16 : 0.30;
        if (street < Crupier.RIVER && skillLevel != Skill.RECREATIONAL && currentProfile != Profile.NIT
                && activePlayers <= 2
                && ppot > semiBluffPpot && npot < 0.15 && effectiveStrength < STRENGTH_VALUE_BET_DRAW
                && boardTexture.totalScore <= 3
                && randInt(100) < (skillLevel == Skill.SHARK ? 45 : 28)
                && profitableBluffSize(raise, equity, range, checkEv, pot, boardTexture, street)) {
            logVerbose("Semi-bluff with strong draw.");
            return GamePlayerController.BET;
        }

        if (streetPlan != PLAN_NONE && skillLevel != Skill.RECREATIONAL) {
            int streetsInPlan = street - streetPlanStartStreet;
            switch (streetPlan) {
                case PLAN_BET_BET_BET:
                    if ((effectiveStrength > STRENGTH_VALUE_BET_DRAW && valueBetPays)
                            || (effectiveStrength < 0.30
                            && profitableBluffSize(raise, equity, range, checkEv, pot, boardTexture, street))) {
                        logVerbose("Executing PLAN_BET_BET_BET.");
                        return GamePlayerController.BET;
                    }
                    break;
                case PLAN_BET_CHECK_BET:
                    if (streetsInPlan == 0 || streetsInPlan == 2) {
                        if (effectiveStrength > 0.45 && valueBetPays) {
                            logVerbose("Executing active phase of PLAN_BET_CHECK_BET.");
                            return GamePlayerController.BET;
                        }
                    } else {
                        logVerbose("Executing check phase of PLAN_BET_CHECK_BET.");
                        return GamePlayerController.CHECK;
                    }
                    break;
                case PLAN_CHECK_CALL:
                    logVerbose("Executing check phase of PLAN_CHECK_CALL trap.");
                    return GamePlayerController.CHECK;
            }
        }

        boolean boardTooScary = boardTexture.totalScore >= 5 && effectiveStrength < STRENGTH_VALUE_RAISE;
        double valueThreshold = activePlayers >= 4 ? STRENGTH_VALUE_BET + 0.10 : STRENGTH_VALUE_BET;
        if (valueBetPays && effectiveStrength > valueThreshold && !boardTooScary) {
            logVerbose("Standard Value Bet based on EV.");
            return GamePlayerController.BET;
        } else if (boardTooScary && valueBetPays) {
            logVerbose("Value bet blocked: Board texture too dangerous for current strength.");
        }

        if (valueBetPays && skillLevel == Skill.RECREATIONAL && onTilt && effectiveStrength > 0.35) {
            logVerbose("Tilt Bet (Recreational value threshold).");
            return GamePlayerController.BET;
        }

        logVerbose("No profitable bet found. Checking.");
        return GamePlayerController.CHECK;
    }

    private int decisionWhenFacingBet(double effectiveStrength, double winProb, double evCall,
            RaiseEvaluation raise, double ppot, double pot, double callCost, int street, int betCount,
            int activePlayers, boolean potCommitted, BoardTexture boardTexture,
            GameOpponentStats targetStats, boolean unimprovedPocketPair, boolean certainRiver) {
        cBetConsidered = true;
        floatPlay = false;
        boolean raisePays = improvesEv(raise.ev(), Math.max(0, evCall));
        double betRatio = callCost / (pot > 0 ? pot : 1);

        if (raisePays && skillLevel != Skill.RECREATIONAL && currentProfile != Profile.STATION && betCount == 1 && street < Crupier.RIVER) {
            boolean hasOvercards = false;
            if (unimprovedPocketPair) {
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
                return GamePlayerController.BET;
            }
        }

        if (activePlayers <= 2 && callCost <= pot * 0.6
                && canFloat(effectiveStrength, betCount, street, boardTexture)
                && profitableFloat(winProb, evCall, raise.ev(), pot + callCost, boardTexture)) {
            floatPlay = true;
            logVerbose("Initiating Float Strategy (Calling to bluff later).");
            return GamePlayerController.CHECK;
        }

        double adjustedEvCall = evCall;
        if (!certainRiver && betRatio > 0.50) {
            if (currentProfile == Profile.STATION) {
                if (effectiveDifficulty() != Difficulty.HARD) {
                    adjustedEvCall += pot * 0.15;
                }
            } else if (currentProfile == Profile.NIT) {
                adjustedEvCall -= pot * 0.20;
            } else if (skillLevel == Skill.REGULAR && effectiveStrength < 0.70) {
                adjustedEvCall -= pot * 0.10;
            }
        }
        if (!certainRiver && skillLevel == Skill.RECREATIONAL && onTilt) {
            adjustedEvCall += pot * 0.12;
        }

        boolean allowsValueRaise = currentProfile != Profile.STATION
                || (effectiveDifficulty() == Difficulty.HARD && effectiveStrength >= STRENGTH_NUT_TRAP
                && raise.ev() > evCall + 0.08 * pot);
        if (potCommitted && evCall > 0) {
            if (effectiveStrength >= STRENGTH_NUT_TRAP && raisePays && allowsValueRaise) {
                logVerbose("Pot committed and very strong. Shoving (Re-raising).");
                return GamePlayerController.BET;
            }
            logVerbose("Pot committed. Calling down.");
            return GamePlayerController.CHECK;
        }

        // Threshold for raise-for-value drops on HARD so sharks generate the
        // AF=2-3 aggression industry regulars show, rather than calling down.
        double valueRaiseThreshold = STRENGTH_VALUE_RAISE;
        if (effectiveDifficulty() == Difficulty.HARD) {
            valueRaiseThreshold = 0.72;
        }
        if (raisePays && raise.ev() > adjustedEvCall && effectiveStrength > valueRaiseThreshold && allowsValueRaise) {
            logVerbose("Raising for value. High EV.");
            return GamePlayerController.BET;
        }

        // Medium-strength hands need a plausible way to improve or make
        // the bettor fold. On the river those hands usually bluff-catch.
        boolean mediumRaiseEligible = effectiveStrength >= 0.40
                && effectiveStrength < valueRaiseThreshold
                && raisePays
                && activePlayers <= 2
                && currentProfile != Profile.STATION
                && currentProfile != Profile.NIT;
        if (mediumRaiseEligible) {
            int chance;
            if (effectiveDifficulty() == Difficulty.HARD) {
                boolean semiBluff = street < Crupier.RIVER && ppot > 0.14 && raise.foldEquity() > 0.12;
                chance = semiBluff ? (skillLevel == Skill.SHARK ? 55 : 30) : 0;
            } else if (effectiveDifficulty() == Difficulty.MEDIUM) {
                chance = (skillLevel == Skill.SHARK) ? 30
                        : (currentProfile == Profile.LAG) ? 18
                                : (currentProfile == Profile.TAG) ? 10 : 0;
            } else {
                chance = 0; // EASY stays passive
            }
            if (chance > 0 && randInt(100) < chance) {
                logVerbose("Mixed heads-up raise.");
                return GamePlayerController.BET;
            }
        }

        if (raisePays && skillLevel == Skill.SHARK && betCount == 1 && activePlayers > 3
                && effectiveStrength > 0.60 && raise.foldEquity() > 0 && randInt(100) < 20) {
            logVerbose("Executing Squeeze Play against multiple callers.");
            return GamePlayerController.BET;
        }

        if (streetPlan == PLAN_CHECK_CALL) {
            boolean overbetThreshold = betRatio > 1.2;
            boolean nitThreshold = targetStats != null && targetStats.isNit() && betRatio > 0.6;

            if (overbetThreshold || nitThreshold) {
                logVerbose("Pain threshold exceeded (Overbet or strong bet from Nit). Aborting PLAN_CHECK_CALL.");
                streetPlan = PLAN_NONE;
            } else if (evCall > 0 && effectiveStrength > STRENGTH_CALLDOWN_TRAP
                    && skillLevel != Skill.RECREATIONAL) {
                logVerbose("Executing trap call from PLAN_CHECK_CALL.");
                return GamePlayerController.CHECK;
            }
        }

        if (activePlayers == 2 && betCount == 1 && street == Crupier.FLOP
                && profitableFlopCounterRaise(winProb, ppot, pot, callCost, evCall, boardTexture, targetStats)) {
            return GamePlayerController.BET;
        }

        if (adjustedEvCall > 0) {
            logVerbose("Calling due to positive adjusted EV.");
            return GamePlayerController.CHECK;
        }

        if (skillLevel == Skill.RECREATIONAL && currentProfile == Profile.STATION && effectiveStrength > 0.25) {
            logVerbose("Station calling out of profile habit despite negative EV.");
            return GamePlayerController.CHECK;
        }

        logVerbose("Folding. No profitable action.");
        return GamePlayerController.FOLD;
    }

    static double showdownEv(double equity, double winnings, double cost) {
        return equity * winnings - (1.0 - equity) * cost;
    }

    private boolean profitableFlopCounterRaise(double equity, double ppot, double pot, double callCost,
            double callEv, BoardTexture texture, GameOpponentStats stats) {
        if (effectiveDifficulty() != Difficulty.HARD || skillLevel == Skill.RECREATIONAL || currentProfile == Profile.STATION
                || stats == null || stats.isManiac() || stats.postflopAction(Crupier.FLOP) != PostflopAction.BET
                || !stats.raisedPreflopThisHand() || texture.totalScore >= 4 || callCost > 0.45 * pot
                || equity < 0.15 || equity > 0.64 || (ppot < 0.12 && equity < 0.36)) {
            return false;
        }
        DealerView table = dealer();
        double wager = table.getApuesta_actual();
        double target = finalBetTarget(BetRules.minRaiseTo(wager, table.getUltimo_raise(), table.getCiega_grande()));
        double cost = target - cpuPlayer.getBet();
        if (!canRaiseTo(target) || effectiveStackBehind() < 1.5 * cost) {
            return false;
        }
        double open = stats.openingBetRate(Crupier.FLOP, 0.66);
        double continueRange = 1 - stats.foldToBetRate(Crupier.FLOP, false, 0.52);
        double foldPrior = Math.max(0, 1 - continueRange / Math.max(0.01, open));
        double folds = Math.min(FOLD_EQ_CAP, stats.foldToBetRate(Crupier.FLOP, true, foldPrior));
        if (folds < 0.20) {
            return false;
        }
        ShowdownOdds raw = cachedEvaluation.currentOdds();
        double lossWhenCalled = raw.equity(1) - equityAgainstStrongestHands(raw, 1 - folds);
        double whenCalled = clampEquity(equity - 0.60 * lossWhenCalled);
        double raiseEv = folds * pot + (1 - folds) * showdownEv(whenCalled, pot + target - wager, cost);
        if (raiseEv <= Math.max(0, callEv) + 0.05 * pot || randInt(100) >= 65) {
            return false;
        }
        lastBetSize = target;
        logVerbose("Small semi-bluff raise against a frequent continuation bettor.");
        return true;
    }

    private static boolean improvesEv(double candidate, double alternative) {
        // Ignore differences smaller than half a cent in an estimated EV.
        return candidate - alternative > 0.005;
    }

    private static double clampEquity(double probability) {
        return Math.max(0, Math.min(1, probability));
    }

    private static double equityAgainstStrongestHands(ShowdownOdds odds, double fraction) {
        // A tight bettor's strongest hands beat us first, then tie, then lose.
        // The caller applies only part of this penalty because bets also bluff
        // and the observed frequency is measured against a preselected range.
        double wins = Math.max(0, fraction - (1 - odds.win()));
        double ties = Math.max(0, fraction - (1 - odds.win() - odds.tie())) - wins;
        return (wins + 0.5 * ties) / fraction;
    }

    private record RaiseEvaluation(double ev, double foldEquity) {
        private static final RaiseEvaluation UNAVAILABLE = new RaiseEvaluation(Double.NEGATIVE_INFINITY, 0);
    }

    private RaiseEvaluation selectPostflopRaise(double strength, int opponents, double[] equity, RangeEquity range,
            BoardTexture texture, int betCount, int street, double pot) {
        lastBetSize = finalBetTarget(getBetSize(strength));
        RaiseEvaluation preferred = betCount < MAX_BET_COUNT && canRaiseTo(lastBetSize)
                ? evaluatePotAction(lastBetSize - cpuPlayer.getBet(), lastBetSize, equity, range,
                        texture, betCount, street, pot)
                : RaiseEvaluation.UNAVAILABLE;

        DealerView table = dealer();
        double wager = table.getApuesta_actual();
        if (effectiveDifficulty() == Difficulty.HARD && wager == 0 && table.getPlayersInSeatingOrder().size() >= 3
                && betCount < MAX_BET_COUNT && table.getCiega_pequeña() > 0) {
            double chip = table.getCiega_pequeña();
            double small = finalBetTarget(Math.max(table.getCiega_grande(), Math.ceil(0.18 * pot / chip) * chip));
            if (small < lastBetSize && canRaiseTo(small)) {
                RaiseEvaluation candidate = evaluatePotAction(small - cpuPlayer.getBet(), small, equity, range,
                        texture, betCount, street, pot);
                if (candidate.ev() > preferred.ev() + 0.02 * pot) {
                    lastBetSize = small;
                    return candidate;
                }
            }
        }
        if (effectiveDifficulty() != Difficulty.HARD || opponents != 1 || betCount >= MAX_BET_COUNT
                || MoneyMath.compare(wager, 0) <= 0) {
            return preferred;
        }
        double minimum = finalBetTarget(BetRules.minRaiseTo(wager, table.getUltimo_raise(), table.getCiega_grande()));
        if (MoneyMath.compare(minimum, lastBetSize) >= 0 || !canRaiseTo(minimum)) {
            return preferred;
        }
        RaiseEvaluation smaller = evaluatePotAction(minimum - cpuPlayer.getBet(), minimum, equity, range,
                texture, betCount, street, pot);
        if (improvesEv(smaller.ev(), preferred.ev())) {
            lastBetSize = minimum;
            return smaller;
        }
        return preferred;
    }

    private double finalBetTarget(double target) {
        double alreadyBet = cpuPlayer.getBet();
        double stack = cpuPlayer.getStack();
        return BetRules.botBetUsesWholeStack(stack, target - alreadyBet) ? alreadyBet + stack : target;
    }

    private boolean canRaiseTo(double target) {
        DealerView table = dealer();
        double wager = table.getApuesta_actual();
        if (!table.canPlayerRaise(cpuPlayer.getNickname()) || MoneyMath.compare(target, wager) <= 0) {
            return false;
        }
        double minTarget = MoneyMath.compare(wager, 0) == 0 ? table.getCiega_grande()
                : BetRules.minRaiseTo(wager, table.getUltimo_raise(), table.getCiega_grande());
        if (MoneyMath.compare(target - cpuPlayer.getBet(), cpuPlayer.getStack()) < 0
                && MoneyMath.compare(target, minTarget) < 0) {
            return false;
        }
        for (BotPlayerView seat : table.getPlayersInSeatingOrder()) {
            if (isOpponentInHand(seat) && MoneyMath.compare(seat.getBet() + seat.getStack(), wager) > 0) {
                return true;
            }
        }
        return false;
    }

    private RaiseEvaluation evaluatePotAction(double cost, double target, double[] equity, RangeEquity range,
            BoardTexture texture, int betCount, int street, double pot) {
        DealerView table = dealer();
        List<? extends BotPlayerView> tableSeats = table.getPlayersInSeatingOrder();
        List<BotPotOdds.Seat> seats = new ArrayList<>(tableSeats.size());
        double allFold = 1;
        int liveOpponents = 0;
        int modeledSeat = -1;
        int otherModeledSeat = -1;
        JointEquity joint = range == null ? null : range.joint();
        int[] opponentBits = joint == null ? null : new int[tableSeats.size() - 1];
        // A call can leave other players owing chips at the current price.
        // Their matching chips can change which players contest each layer.
        boolean pendingCalls = target == 0 && cost > 0 && equity.length > 2
                && effectiveDifficulty() == Difficulty.HARD;
        double matchTarget = pendingCalls ? table.getApuesta_actual() : target;
        double potWhenResponding = pendingCalls ? pot + cost : pot;
        boolean checkedToInPosition = target > 0 && equity.length == 2 && street == Crupier.RIVER
                && betCount == 0 && effectiveDifficulty() == Difficulty.HARD && isInPositionPostflop();
        for (BotPlayerView seat : tableSeats) {
            if (cpuPlayer.getNickname().equals(seat.getNickname())) {
                continue;
            }
            boolean live = isOpponentInHand(seat);
            if (live) {
                if (opponentBits != null) {
                    opponentBits[seats.size()] = 1 << liveOpponents;
                }
                liveOpponents++;
                if (range != null && seat.getNickname().equals(range.opponent())) {
                    modeledSeat = seats.size();
                } else if (range != null && seat.getNickname().equals(range.otherOpponent())) {
                    otherModeledSeat = seats.size();
                }
            }
            double added = live ? Math.max(0, Math.min(seat.getStack(), matchTarget - seat.getBet())) : 0;
            double fold = live ? foldProbability(seat, texture, betCount, street, potWhenResponding,
                    added, checkedToInPosition) : 1;
            seats.add(new BotPotOdds.Seat(PotMath.toCents(table.matchedContribution(seat)),
                    PotMath.toCents(added), fold));
            allFold *= fold;
        }
        double[] modeledEquity = modeledSeat < 0 ? null : range.shares();
        if (target > 0 && liveOpponents == 1 && allFold > 0 && effectiveDifficulty() == Difficulty.HARD) {
            // Folding and showdown strength are correlated. Reweight the
            // caller toward the stronger part of their starting range.
            ShowdownOdds odds = modeledEquity == null ? cachedEvaluation.currentOdds() : range.odds();
            double penalty = odds.equity(1) - equityAgainstStrongestHands(odds, 1 - allFold);
            double weight = street == Crupier.RIVER ? 0.85 : 0.55;
            double minimumShare = street == Crupier.RIVER && odds.win() + odds.tie() >= 1 - 1e-12 ? 0.5 : 0;
            double[] starting = modeledEquity == null ? equity : modeledEquity;
            double[] called = {1, Math.max(minimumShare, starting[1] - weight * penalty)};
            if (modeledEquity == null) {
                equity = called;
            } else {
                modeledEquity = called;
            }
        }
        long committed = PotMath.toCents(table.matchedContribution(cpuPlayer));
        double ev;
        if (joint != null) {
            ev = BotPotOdds.ev(committed, PotMath.toCents(cost), PotMath.toCents(pot), seats, eligible -> {
                int mask = 0;
                while (eligible != 0) {
                    mask |= opponentBits[Integer.numberOfTrailingZeros(eligible)];
                    eligible &= eligible - 1;
                }
                return joint.share(mask);
            });
        } else if (modeledSeat >= 0 && otherModeledSeat >= 0) {
            int first = 1 << modeledSeat;
            int other = 1 << otherModeledSeat;
            ev = BotPotOdds.ev(committed, PotMath.toCents(cost), PotMath.toCents(pot), seats, eligible -> {
                if ((eligible & first) != 0) {
                    return range.shares()[Integer.bitCount(eligible)];
                }
                return (eligible & other) != 0 ? range.otherShare() : 1;
            });
        } else {
            ev = BotPotOdds.ev(committed, PotMath.toCents(cost), PotMath.toCents(pot), seats,
                    equity, modeledSeat, modeledEquity);
        }
        return new RaiseEvaluation(ev, allFold);
    }

    private double foldProbability(BotPlayerView opponent, BoardTexture boardTexture,
            int betCount, int street, double pot, double amountToMatch, boolean checkedToInPosition) {
        if (skillLevel == Skill.RECREATIONAL || MoneyMath.compare(amountToMatch, 0) <= 0) {
            return 0;
        }
        double foldProbability = 0.20;
        if (boardTexture.totalScore <= 1) {
            foldProbability += 0.08;
        } else if (boardTexture.totalScore >= 4) {
            foldProbability -= 0.08;
        }
        if (street == Crupier.RIVER) {
            foldProbability -= 0.05;
        }
        boolean invested = betCount > 0 && MoneyMath.compare(opponent.getBet(), 0) > 0;
        if (betCount == 0) {
            foldProbability += 0.05;
        } else if (betCount >= 2) {
            foldProbability -= 0.10;
        }

        GameOpponentStats stats = opponentStats(opponent);
        if (stats != null) {
            if (stats.isStation() || stats.looksPassiveStation()) {
                foldProbability = 0;
            } else if (stats.isNit()) {
                foldProbability += 0.15;
            } else if (stats.isManiac()) {
                foldProbability -= 0.10;
            }
        }
        double pressure = amountToMatch / Math.max(pot, dealer().getCiega_grande());
        if (foldProbability > 0) {
            foldProbability += Math.max(-0.08, Math.min(0.18, (pressure - DRY_BOARD_FRACTION) * 0.25));
        }
        if (invested && effectiveDifficulty() == Difficulty.HARD) {
            foldProbability *= 0.55;
        }
        double prior = Math.max(0, Math.min(FOLD_EQ_CAP, foldProbability));
        if (stats == null || effectiveDifficulty() != Difficulty.HARD) {
            return prior;
        }
        double estimate = stats.foldToBetRate(street, invested, prior);
        if (checkedToInPosition && !stats.isManiac()) {
            // A player who checked has passed up a chance to bet their
            // strongest hands. Discount that information for traps and bluffs.
            double removed = 0.70 * stats.openingBetRate(street, 0.35);
            estimate += Math.min(0.20, estimate * removed / (1 - removed));
        }
        return Math.min(FOLD_EQ_CAP, estimate);
    }

    private boolean profitableSmallProbe(double checkEv, double pot, int street) {
        if (effectiveDifficulty() != Difficulty.HARD || aggressiveLine) {
            return false;
        }
        DealerView table = dealer();
        int seats = table.getJugadoresActivos();
        boolean station = currentProfile == Profile.STATION;
        boolean stationAtMultiwayTable = station && seats >= 4;
        boolean threeHandedStation = station && seats == 3;
        double checkLimit = stationAtMultiwayTable ? 0.56 : 0.40;
        if (threeHandedStation) {
            checkLimit = 0.30;
        }
        if (table.getCiega_pequeña() <= 0 || seats < 2 || (seats < 4 && !station) || checkEv > checkLimit * pot) {
            return false;
        }
        double size = finalBetTarget(table.getCiega_grande());
        double cost = size - cpuPlayer.getBet();
        if (cost > (stationAtMultiwayTable ? 0.36 : 0.30) * pot || !canRaiseTo(size)) {
            return false;
        }
        for (BotPlayerView opponent : table.getPlayersInSeatingOrder()) {
            if (!isOpponentInHand(opponent)) {
                continue;
            }
            GameOpponentStats stats = opponentStats(opponent);
            if (stats == null || stats.isStation()
                    || stats.looksPassiveStation() || stats.isManiac()) {
                return false;
            }
            boolean checked = stats.postflopAction(street) == PostflopAction.CHECK;
            double prior = stationAtMultiwayTable ? (checked ? 0.56 : 0.52) : (checked ? 0.53 : 0.49);
            double folds = stats.foldToBetRate(street, false, prior);
            // Price the probe as if it lost whenever called.
            double profit = folds * pot - (1 - folds) * cost;
            double margin = threeHandedStation ? Math.max(0.09 * pot, 0.55 * cost) : 0.04 * pot;
            if (profit > checkEv + margin) {
                lastBetSize = size;
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean profitableBluffSize(RaiseEvaluation raise, double[] equity, RangeEquity range, double checkEv,
            double pot, BoardTexture texture, int street) {
        if (raise.foldEquity() > 0 && improvesEv(raise.ev(), checkEv)) {
            return true;
        }
        DealerView table = dealer();
        double chip = table.getCiega_pequeña();
        if (chip <= 0) {
            chip = table.getInitialSmallBlind();
        }
        if (chip <= 0) {
            return false;
        }
        double fraction = street == Crupier.RIVER && effectiveDifficulty() == Difficulty.HARD ? 0.20 : DRY_BOARD_FRACTION;
        double smallBet = finalBetTarget(Math.max(table.getCiega_grande(), Math.ceil(pot * fraction / chip) * chip));
        if (smallBet >= lastBetSize || table.getConta_bet() >= MAX_BET_COUNT || !canRaiseTo(smallBet)) {
            return false;
        }
        RaiseEvaluation smaller = evaluatePotAction(smallBet - cpuPlayer.getBet(), smallBet,
                equity, range, texture, dealer().getConta_bet(), street, pot);
        if (smaller.foldEquity() <= 0 || !improvesEv(smaller.ev(), checkEv)) {
            return false;
        }
        lastBetSize = smallBet;
        return true;
    }

    private boolean isOpponentInHand(BotPlayerView seat) {
        return !cpuPlayer.getNickname().equals(seat.getNickname()) && dealer().isContender(seat);
    }

    private double callCost() {
        double required = MoneyMath.clean(dealer().getApuesta_actual() - cpuPlayer.getBet());
        return Math.max(0, Math.min(required, cpuPlayer.getStack()));
    }

    private double contestablePot(double extraChips) {
        DealerView table = dealer();
        double covered = table.matchedContribution(cpuPlayer) + extraChips;
        double pot = table.getBote_total();
        double excess = 0;
        boolean deeperOpponent = false;
        for (BotPlayerView seat : table.getPlayersInSeatingOrder()) {
            double committed = table.matchedContribution(seat);
            if (!cpuPlayer.getNickname().equals(seat.getNickname())
                    && MoneyMath.compare(committed, covered) > 0) {
                excess += committed - covered;
                deeperOpponent |= table.isContender(seat);
            }
        }
        // The highest eligible layer also contains folded players' excess.
        return Math.max(0, deeperOpponent ? pot - excess : pot);
    }

    private double effectiveStackBehind() {
        double behind = 0;
        double wager = dealer().getApuesta_actual();
        for (BotPlayerView seat : dealer().getPlayersInSeatingOrder()) {
            if (isOpponentInHand(seat)) {
                behind = Math.max(behind, seat.getStack() - Math.max(0, wager - seat.getBet()));
            }
        }
        return Math.min(cpuPlayer.getStack() - callCost(), behind);
    }

    private boolean profitableFloat(double equity, double callEv, double raiseEv,
            double turnPot, BoardTexture texture) {
        double risk = Math.min(effectiveStackBehind(), Math.max(dealer().getCiega_grande(),
                turnPot * DRY_BOARD_FRACTION));
        if (MoneyMath.compare(risk, 0) <= 0) {
            return false;
        }
        for (BotPlayerView opponent : dealer().getPlayersInSeatingOrder()) {
            if (isOpponentInHand(opponent)) {
                double fold = foldProbability(opponent, texture, 0, Crupier.TURN, turnPot, risk, false);
                double betEv = fold * turnPot + (1 - fold) * showdownEv(equity, turnPot + risk, risk);
                double gain = betEv - equity * turnPot;
                // We have no turn check frequency. Credit only half the gain
                // of the favorable branch and reprice the real turn separately.
                double alternative = Math.max(0, Math.max(callEv, raiseEv));
                return fold > 0 && improvesEv(callEv + 0.5 * gain, alternative);
            }
        }
        return false;
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
        if (effectiveDifficulty() == Difficulty.EASY) {
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

    private int preflopHandTier() {
        int high = Math.max(holeCard1.getRank(), holeCard2.getRank());
        int low = Math.min(holeCard1.getRank(), holeCard2.getRank());
        return evaluateHandTier(high, low, high == low,
                holeCard1.getSuit() == holeCard2.getSuit(), high - low);
    }

    private int closedPreflopDecision(DealerView table, int betCount) {
        double cost = callCost();
        if (cost == 0) {
            return GamePlayerController.NODEC;
        }
        BotPlayerView opponent = null;
        for (BotPlayerView seat : table.getPlayersInSeatingOrder()) {
            if (isOpponentInHand(seat)) {
                if (opponent != null) {
                    return GamePlayerController.NODEC;
                }
                opponent = seat;
            }
        }
        if (opponent == null || (MoneyMath.compare(cost, cpuPlayer.getStack()) < 0
                && MoneyMath.compare(opponent.getStack(), 0) > 0)) {
            return GamePlayerController.NODEC;
        }

        double[][] weights = null;
        if (opponent.getNickname().equals(table.getPreflopAggressorNick())) {
            double wagerInBlinds = table.getApuesta_actual() / table.getCiega_grande();
            GameOpponentStats stats = opponentStats(opponent);
            double openingRate = stats == null ? 0.28 : stats.preflopRaiseRate(0.28);
            // Small shoves can be wide. A deep shove or a re-raise represents
            // a narrower subset of the same seat's preflop aggression.
            double width = Math.min(0.85, openingRate * (wagerInBlinds <= 5 ? 1.8 : wagerInBlinds <= 15 ? 1 : 0.60));
            if (betCount >= 2) {
                width *= betCount == 2 ? 0.65 : 0.40;
            }
            weights = StartingHandRanges.weights(width);
        }
        double equity = evaluator.preflopAllInEquity(holeCard1.getIndex(), holeCard2.getIndex(), weights);
        if (!Double.isFinite(equity)) {
            return GamePlayerController.NODEC;
        }
        double pot = contestablePot(cost);
        // Use a small margin on raised pots for estimation and range uncertainty.
        double margin = weights == null ? 0 : 0.015;
        return showdownEv(equity - margin, pot, cost) >= 0
                ? GamePlayerController.CHECK : GamePlayerController.FOLD;
    }

    private int calculatePreflopAction(int betCount, int activePlayers, int handTier) {
        int startingTier = handTier;
        Position pos = determinePosition();
        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);
        if (onTilt && skillLevel == Skill.RECREATIONAL) {
            if (handTier == 5) {
                handTier = 4;
            }
            if (handTier == 4) {
                handTier = 3;
            }
        }

        if (skillLevel != Skill.RECREATIONAL && effectiveDifficulty() != Difficulty.EASY) {
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

        if (betCount > 0 && handTier >= 2 && skillLevel != Skill.RECREATIONAL) {
            double cost = callCost();
            double maxCostToPot = handTier == 2 ? 0.80 : 0.65;
            if (cost > 4 * dealer().getCiega_grande()
                    && cost > maxCostToPot * contestablePot(cost)
                    && effectiveStackBehind() < 4 * cost) {
                logVerbose("Preflop fold: steep call price with little effective stack behind.");
                return GamePlayerController.FOLD;
            }
        }

        if (currentProfile == Profile.STATION && handTier <= 4 && betCount < 2) {
            if (effectiveDifficulty() == Difficulty.HARD) {
                if (handTier == 1) {
                    return GamePlayerController.BET;
                }
                DealerView table = dealer();
                String name = cpuPlayer.getNickname();
                if (betCount == 0 && table.getLimpersCount() == 0 && !name.equals(table.getBb_nick())
                        && !name.equals(table.getSb_nick())) {
                    return handTier <= 2 || (handTier == 3 && pos == Position.LATE)
                            ? GamePlayerController.BET : GamePlayerController.FOLD;
                }
                if (betCount == 0 && name.equals(table.getSb_nick())
                        && table.getJugadoresActivos() == 2 && table.getLimpersCount() == 0
                        && (startingTier <= 3 || (startingTier == 4 && (isPair || suited || high == 12)))) {
                    // In heads-up, the button has position on every postflop street.
                    return GamePlayerController.BET;
                }
                if (betCount == 1 && startingTier >= 4) {
                    double cost = callCost();
                    double limit = suited || isPair ? 0.36 : 0.27;
                    if (cost > dealer().getCiega_grande() && cost > limit * (contestablePot(cost) + cost)) {
                        return GamePlayerController.FOLD;
                    }
                }
                if (betCount == 1 && handTier == 3 && activePlayers >= 4 && !suited && !isPair && pos != Position.LATE) {
                    double cost = callCost();
                    if (cost > 0.29 * (contestablePot(cost) + cost)) {
                        return GamePlayerController.FOLD;
                    }
                }
            }
            logVerbose("Preflop Station limp/call.");
            return GamePlayerController.CHECK;
        }

        DealerView crupier = dealer();
        String myNick = cpuPlayer.getNickname();
        boolean isSB = myNick.equals(crupier.getSb_nick());
        boolean isBB = myNick.equals(crupier.getBb_nick());

        if (effectiveDifficulty() == Difficulty.HARD && betCount == 1 && activePlayers >= 4 && handTier == 3
                && !suited && !isPair && (high < 12 || low < 10) && cpuPlayer.getBet() == 0
                && (pos == Position.EARLY || pos == Position.MIDDLE)) {
            double cost = callCost();
            if (cost > 0.29 * (contestablePot(cost) + cost)) {
                return GamePlayerController.FOLD;
            }
        }

        if (betCount == 1 && activePlayers >= 3 && handTier <= 3 && effectiveDifficulty() != Difficulty.EASY
                && (skillLevel == Skill.SHARK || (currentProfile == Profile.LAG && skillLevel == Skill.REGULAR))
                && hasSqueezeCaller(crupier)) {
            int squeezeChance = (pos == Position.LATE || pos == Position.BLINDS) ? 35 : 25;
            if (randInt(100) < squeezeChance) {
                logVerbose("Preflop Squeeze.");
                return GamePlayerController.BET;
            }
        }

        if (betCount >= 3) {
            if (handTier == 1 && low >= 10) {
                logVerbose("Preflop 5-Bet (premium only).");
                return GamePlayerController.BET;
            }
            logVerbose("Preflop Fold vs 4-Bet.");
            return GamePlayerController.FOLD;
        }

        if (betCount == 2) {
            if (handTier == 1) {
                logVerbose("Preflop 4-Bet for value.");
                return GamePlayerController.BET;
            }
            if (handTier == 2) {
                logVerbose("Preflop Call vs 3-Bet (TT/JJ/AQ/KQs).");
                return GamePlayerController.CHECK;
            }
            double cost = callCost();
            // A pocket pair flops at least a set about 12% of the time.
            // Discount the stack behind because a set rarely wins it all.
            if (skillLevel != Skill.RECREATIONAL && isPair && high <= 4
                    && contestablePot(cost) + 0.5 * effectiveStackBehind() >= 8 * cost) {
                logVerbose("Preflop Set-mine call vs 3-Bet (small pair).");
                return GamePlayerController.CHECK;
            }
            logVerbose("Preflop Fold vs 3-Bet.");
            return GamePlayerController.FOLD;
        }

        boolean headsUp = (activePlayers == 2);

        if (betCount == 1) {
            if (handTier == 1) {
                logVerbose("Preflop 3-Bet for value.");
                return GamePlayerController.BET;
            }
            boolean threeBetBluffCandidate = (high == 12 && low <= 3 && suited)
                    || (high == 11 && low == 10 && !suited);
            if (skillLevel == Skill.SHARK && threeBetBluffCandidate
                    && activePlayers <= 2
                    && (pos == Position.LATE || pos == Position.BLINDS)
                    && randInt(100) < 30) {
                logVerbose("Preflop 3-Bet bluff (blocker).");
                return GamePlayerController.BET;
            }
            if (handTier <= 3) {
                logVerbose("Preflop Standard Call.");
                return GamePlayerController.CHECK;
            }
            if (isBB && handTier == 4) {
                logVerbose("Preflop BB defend vs steal.");
                return GamePlayerController.CHECK;
            }
            if (handTier == 4 && currentProfile == Profile.LAG && (pos == Position.LATE || pos == Position.BLINDS)) {
                logVerbose("Preflop LAG loose call in position.");
                return GamePlayerController.CHECK;
            }
            // Heads-up BB defends wider than full-ring. Iteration 6 adds an
            // explicit difficulty offset on top of the profile rates so the
            // overall VPIP gradient slopes the right way: EASY plays loose
            // (lots of defends, calling-station style), HARD plays tight
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
                    return GamePlayerController.CHECK;
                }
            }
            logVerbose("Preflop Fold vs Raise.");
            return GamePlayerController.FOLD;
        }

        if (isSB && betCount == 0) {
            boolean opensFromSmallBlind = currentProfile == Profile.NIT ? handTier <= 3 : handTier <= 4;
            if (opensFromSmallBlind) {
                // Tier-4 marginal hands: HARD TAGs/SHARKs fold a slice rather
                // than open every time, keeping VPIP inside the target band.
                if (handTier == 4 && currentProfile != Profile.STATION
                        && currentProfile != Profile.LAG
                        && effectiveDifficulty() == Difficulty.HARD) {
                    int foldChance = 33;
                    if (randInt(100) < foldChance) {
                        logVerbose("Preflop HU SB tier-4 selective fold.");
                        return GamePlayerController.FOLD;
                    }
                }
                logVerbose("Preflop SB raise without a prior raise.");
                return GamePlayerController.BET;
            }
            // Heads-up button opens trash wider than full-ring cutoff/button but
            // not freely. HU button open rates land roughly at: NIT 45%, TAG 60%,
            // STATION 70% (with limps), LAG 70%, SHARK 65% — within the industry
            // 60-80% band.
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
                    return GamePlayerController.BET;
                }
            }
            if (handTier == 5 && (currentProfile == Profile.LAG || skillLevel == Skill.SHARK)) {
                int fallbackChance = clampPct(25 + difficultyLoosenessOffset());
                if (fallbackChance > 0 && randInt(100) < fallbackChance) {
                    logVerbose("Preflop SB folded-to: trash steal.");
                    return GamePlayerController.BET;
                }
            }
            if (currentProfile == Profile.STATION && handTier == 5
                    && randInt(100) < 40) {
                logVerbose("Preflop SB limp-complete.");
                return GamePlayerController.CHECK;
            }
            logVerbose("Preflop SB fold.");
            return GamePlayerController.FOLD;
        }

        switch (pos) {
            case EARLY:
                if (handTier <= 2) {
                    logVerbose("Preflop Early Open.");
                    return GamePlayerController.BET;
                }
                if (handTier == 3 && currentProfile != Profile.NIT) {
                    logVerbose("Preflop Early Loose Open.");
                    return GamePlayerController.BET;
                }
                if (skillLevel == Skill.RECREATIONAL && handTier <= 4 && randInt(100) < 30) {
                    logVerbose("Preflop Rec open limp.");
                    return GamePlayerController.CHECK;
                }
                return GamePlayerController.FOLD;
            case MIDDLE:
                if (handTier <= 3) {
                    logVerbose("Preflop Middle Open.");
                    return GamePlayerController.BET;
                }
                if (handTier == 4 && currentProfile == Profile.LAG) {
                    logVerbose("Preflop Middle LAG Open.");
                    return GamePlayerController.BET;
                }
                return GamePlayerController.FOLD;
            case LATE:
                if (handTier <= 4 && currentProfile != Profile.NIT) {
                    logVerbose("Preflop Steal/Open.");
                    return GamePlayerController.BET;
                }
                if (currentProfile == Profile.NIT && handTier <= 3) {
                    logVerbose("Preflop Nit Open.");
                    return GamePlayerController.BET;
                }
                if (handTier == 5 && activePlayers <= 2
                        && (currentProfile == Profile.LAG || skillLevel == Skill.SHARK)
                        && randInt(100) < 20) {
                    logVerbose("Preflop Trash Steal (Bluff).");
                    return GamePlayerController.BET;
                }
                return GamePlayerController.FOLD;
            case BLINDS:
                if (handTier <= 3 && currentProfile != Profile.NIT) {
                    logVerbose("Preflop BB iso-raise vs limpers.");
                    return GamePlayerController.BET;
                }
                if (handTier == 4 && currentProfile == Profile.LAG) {
                    logVerbose("Preflop BB LAG iso-raise.");
                    return GamePlayerController.BET;
                }
                logVerbose("Preflop BB check option.");
                return GamePlayerController.CHECK;
            default:
                return GamePlayerController.FOLD;
        }
    }

    private boolean hasSqueezeCaller(DealerView table) {
        String opener = table.getPreflopAggressorNick();
        if (opener == null) {
            return false;
        }
        for (BotPlayerView seat : table.getPlayersInSeatingOrder()) {
            if (!opener.equals(seat.getNickname()) && isOpponentInHand(seat)
                    && MoneyMath.compare(seat.getStack(), 0) > 0
                    && MoneyMath.compare(seat.getBet(), table.getApuesta_actual()) == 0) {
                return true;
            }
        }
        return false;
    }

    static int evaluateHandTier(int high, int low, boolean isPair, boolean suited, int gap) {
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
        // With a straddle the straddler (utg_nick) acts LAST preflop (the option): that's
        // late position, not early; the real "under the gun" is the next player.
        String straddleUtg = crupier.getStraddleUtgNick();
        if (straddleUtg != null && myNick.equals(crupier.getUtg_nick())) {
            return Position.LATE;
        }
        if (myNick.equals(dealerNick)) {
            return Position.LATE;
        }
        if (myNick.equals(crupier.getSb_nick()) || myNick.equals(crupier.getBb_nick())) {
            return Position.BLINDS;
        }
        if (myNick.equals(straddleUtg != null ? straddleUtg : crupier.getUtg_nick())) {
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

    boolean isInPositionPostflop() {
        DealerView table = dealer();
        List<? extends BotPlayerView> seats = table.getPlayersInSeatingOrder();
        int button = -1;
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).getNickname().equals(table.getDealer_nick())) {
                button = i;
                break;
            }
        }
        if (button < 0) {
            return false;
        }
        // Acting order starts clockwise after the button. Find the last seat
        // that can still act, skipping folds and players already all-in.
        for (int i = 0; i < seats.size(); i++) {
            BotPlayerView seat = seats.get((button - i + seats.size()) % seats.size());
            if (table.isContender(seat) && MoneyMath.compare(seat.getStack(), 0) > 0) {
                return cpuPlayer.getNickname().equals(seat.getNickname());
            }
        }
        return false;
    }

    /**
     * Difficulty-driven shift applied to heads-up tier-4/5 steal and defense
     * percentages. EASY pulls everything looser (more fish-style play), HARD
     * pulls everything tighter (disciplined sharks fold trash). MEDIUM is the
     * neutral baseline. Combined with the profile-specific base rates this
     * gives the per-difficulty VPIP gradient that mixed personality pools alone
     * cannot produce.
     */
    private int difficultyLoosenessOffset() {
        switch (effectiveDifficulty()) {
            case EASY:
                return 25;
            case MEDIUM:
                return -16;
            case HARD:
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
     * Base frequency (percent) at which the bot fires a polarized river bluff
     * with air when fold equity exists. Scales with difficulty so the river
     * betting range stays balanced (value + bluffs) for the strong levels and
     * the weak levels stay transparently value-only — part of the
     * per-difficulty "feel" rather than only the mistake-injection rate.
     */
    private int riverBluffChance() {
        switch (effectiveDifficulty()) {
            case HARD:
                return 50;
            case MEDIUM:
                return 14;
            default:
                return 0; // EASY plays its river face-up
        }
    }

    private boolean hasRiverBluffBlocker() {
        int[] suitRanks = new int[4];
        int boardRanks = 0;
        for (int card : cachedEvaluationBoard) {
            int rank = 1 << (card % 13);
            suitRanks[card / 13] |= rank;
            boardRanks |= rank;
        }
        for (org.alberta.poker.Card hole : new org.alberta.poker.Card[]{holeCard1, holeCard2}) {
            int suited = suitRanks[hole.getSuit()];
            if (Integer.bitCount(suited) == 3
                    && Integer.highestOneBit(0x1FFF & ~suited) == (1 << hole.getRank())) {
                return true;
            }
        }
        int held = (1 << holeCard1.getRank()) | (1 << holeCard2.getRank());
        for (int high = 12; high >= 3; high--) {
            int straight = high == 3 ? 0x100F : 0x1F << (high - 4);
            int missing = straight & ~boardRanks;
            if (Integer.bitCount(missing) == 2) {
                return (held & missing) != 0 && (held & missing) != missing;
            }
        }
        return false;
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

    GameOpponentStats opponentStats(BotPlayerView opponent) {
        String nick = opponent.getNickname();
        GameOpponentStats stats = dealer().getOpponentStats(nick);
        return stats != null ? stats : TRACKER_MEMORY.get(nick);
    }

    /**
     * @return the Alberta engine's suit index for a Corona card's suit
     */
    public static int coronaCardSuit2LokiCardSuit(GameCardController carta) {
        return Bot.SUITS.indexOf(carta.getPalo());
    }

    /**
     * Converts a Corona card index (1-52) to its Alberta engine
     * {@code org.alberta.poker.Card} equivalent.
     */
    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(int carta) {
        int v = (carta - 1) % 13;
        int val = (v == 0 ? 14 : v + 1);
        String palo = CardCode.fromOneBased(carta).suit().wire();
        return new org.alberta.poker.Card(val - 2, Bot.SUITS.indexOf(palo));
    }

    /**
     * @see #coronaIntegerCard2LokiCard(int)
     */
    public static org.alberta.poker.Card coronaIntegerCard2LokiCard(Integer carta) {
        return coronaIntegerCard2LokiCard(carta.intValue());
    }

    /**
     * Converts a Corona card to its Alberta engine
     * {@code org.alberta.poker.Card} equivalent.
     */
    public static org.alberta.poker.Card coronaCard2LokiCard(GameCardController carta) {
        return new org.alberta.poker.Card(carta.getValorNumerico() - 2, Bot.coronaCardSuit2LokiCardSuit(carta));
    }
}
