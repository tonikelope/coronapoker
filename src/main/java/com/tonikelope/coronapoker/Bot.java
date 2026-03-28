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
 * THE "PERFECT" REALISTIC ENGINE (V3 - Final) - Double-Axis System: Skill
 * (Recreational, Regular, Shark) + Profile (Tag, Lag, Station, Nit). - Restored
 * Opponent Tracking: Skilled bots dynamically adjust their Fold Equity and Call
 * logic based on the human player's historical tendencies. - Crupier
 * Compatibility: All original API converters restored.
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

    public enum Skill {
        RECREATIONAL, REGULAR, SHARK
    }

    private volatile RemotePlayer cpuPlayer = null;
    private volatile Profile baseProfile;
    private volatile Profile currentProfile;
    private volatile Skill skillLevel;

    private volatile org.alberta.poker.Card holeCard1 = null;
    private volatile org.alberta.poker.Card holeCard2 = null;
    private volatile int callCount = 0;
    private volatile boolean slowPlay = false;
    private volatile boolean cBetInitiative = false;

    private volatile double previousStrength = -1.0;
    private volatile int previousStreet = -1;
    private volatile boolean scareCardDetected = false;

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
    }

    public Bot(RemotePlayer player) {
        this.cpuPlayer = player;
        assignPersonality();
    }

    /**
     * Logically distributes Skill and Profile so they don't contradict each
     * other.
     */
    private void assignPersonality() {
        int skillRoll = Helpers.CSPRNG_GENERATOR.nextInt(100);
        int styleRoll = Helpers.CSPRNG_GENERATOR.nextInt(100);

        // 1. Determine Skill Level
        if (skillRoll < 30) {
            skillLevel = Skill.RECREATIONAL;      // 30% Weak players
        } else if (skillRoll < 90) {
            skillLevel = Skill.REGULAR;      // 60% Solid Club Players
        } else {
            skillLevel = Skill.SHARK;                            // 10% Dangerous Pros
        }
        // 2. Determine Profile based on Skill (Logical distribution)
        if (skillLevel == Skill.RECREATIONAL) {
            if (styleRoll < 60) {
                baseProfile = Profile.STATION;    // Mostly passive callers
            } else if (styleRoll < 85) {
                baseProfile = Profile.LAG;   // Some crazy maniacs
            } else {
                baseProfile = Profile.NIT;                       // Few scared rocks
            }
        } else if (skillLevel == Skill.REGULAR) {
            if (styleRoll < 70) {
                baseProfile = Profile.TAG;        // Mostly standard solid
            } else if (styleRoll < 90) {
                baseProfile = Profile.NIT;   // Some overly tight
            } else {
                baseProfile = Profile.LAG;                       // Few aggressive regs
            }
        } else { // SHARK
            if (styleRoll < 75) {
                baseProfile = Profile.TAG;        // Optimal aggressive
            } else {
                baseProfile = Profile.LAG;                       // Hyper-aggressive optimal
            }
        }

        currentProfile = baseProfile;
    }

    private void adjustProfileElasticity() {
        Crupier dealer = GameFrame.getInstance().getCrupier();
        float stack = cpuPlayer.getStack();
        float blindsCost = dealer.getCiega_grande() + dealer.getCiega_pequeña();
        float mRatio = stack / (blindsCost > 0 ? blindsCost : 1);

        // Sharks and Regs adapt to stack sizes. Recreationals just play their base style.
        if (skillLevel != Skill.RECREATIONAL) {
            if (mRatio < 12.0f && baseProfile == Profile.LAG) {
                currentProfile = Profile.TAG;
            } else if (mRatio > 60.0f && baseProfile == Profile.NIT) {
                currentProfile = Profile.TAG;
            } else {
                currentProfile = baseProfile;
            }
        } else {
            currentProfile = baseProfile;
        }
    }

    public void resetBot() {
        holeCard1 = Bot.coronaCard2LokiCard(cpuPlayer.getHoleCard1());
        holeCard2 = Bot.coronaCard2LokiCard(cpuPlayer.getHoleCard2());

        adjustProfileElasticity();

        // Only skilled players slowplay properly
        if (skillLevel == Skill.RECREATIONAL) {
            slowPlay = false;
        } else {
            slowPlay = Helpers.CSPRNG_GENERATOR.nextInt(100) < (currentProfile == Profile.TAG ? 15 : 5);
        }

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
                targetBet = Math.min(minRaise * 3.0f, (pot > 0 ? pot * 1.5f : minRaise * 4.0f));
            } else {
                int limpers = dealer.getLimpersCount();
                targetBet = (2.5f + (limpers * 1.5f)) * bb;
            }
        } else {
            int textureScore = calculateBoardTexture();

            // Skill dictates sizing logic. Recreationals use simple sizing, skilled use texture.
            if (skillLevel == Skill.RECREATIONAL) {
                targetBet = pot * 0.50f; // Simple half pot
            } else {
                if (textureScore >= 3) {
                    targetBet = pot * 0.70f; // Protect on wet boards
                } else if (textureScore == 2) {
                    targetBet = pot * 0.55f;
                } else {
                    targetBet = pot * 0.35f;
                }
            }

            // Sharks use polarized overbets occasionally
            if (skillLevel == Skill.SHARK && dealer.getStreet() == Crupier.RIVER && Helpers.CSPRNG_GENERATOR.nextInt(100) < 20) {
                targetBet = pot * 1.25f;
            }
        }

        // Noise
        if (Helpers.CSPRNG_GENERATOR.nextInt(100) < 25) {
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

        if (street == Crupier.PREFLOP) {
            int decision = calculatePreflopAction(betCount);
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
        if (callCost > 0) {
            // Recreationals commit easier (they get married to their hands)
            float commitAdjust = (skillLevel == Skill.RECREATIONAL) ? -0.05f : 0f;
            if (spr <= 2.0f && effectiveStrength >= (0.80 + commitAdjust)) {
                potCommitted = true;
            } else if (spr <= 4.0f && effectiveStrength >= (0.90 + commitAdjust)) {
                potCommitted = true;
            }
        }

        double winProb = effectiveStrength;
        OpponentTracker targetStats = getPrimaryOpponentStats();

        // COMPLEX LOGIC & TRACKER ANALYSIS (Skilled Bots Only)
        if (skillLevel != Skill.RECREATIONAL) {
            if (betCount > 1 && street >= Crupier.FLOP) {
                winProb -= 0.10; // Respect action
            }
            if (activePlayers > 2) {
                winProb -= 0.05; // Multiway fear
            }
            if (scareCardDetected && currentProfile != Profile.LAG) {
                winProb -= 0.15;
            }
            if (!isInPositionPostflop()) {
                winProb -= 0.03;
            }

            // Analyze Opponent Stats
            if (betCount > 0 && targetStats != null) {
                if (targetStats.isNit()) {
                    winProb -= 0.20; // Nits only bet with the absolute nuts
                } else if (targetStats.isManiac()) {
                    winProb += 0.15; // Maniacs bluff constantly. Play Sheriff.
                }
            }
        }

        winProb = Math.max(0.0, Math.min(1.0, winProb));
        double impliedPot = pot;
        boolean safeDraw = (npot < 0.15);

        // Implied odds calculation
        if (street < Crupier.RIVER && ppot > 0.15 && strength < 0.50 && safeDraw) {
            impliedPot += (cpuPlayer.getStack() * 0.20);
        }

        double evCall = (winProb * impliedPot) - ((1.0 - winProb) * callCost);

        // Fold Equity Calculation based on Opponent Tracking
        double foldEquity = 0.20;
        if (skillLevel == Skill.RECREATIONAL) {
            foldEquity = 0.0; // Fish don't calculate fold equity
        } else if (targetStats != null) {
            if (targetStats.isStation()) {
                foldEquity = 0.0; // NEVER bluff a calling station
            } else if (targetStats.isNit()) {
                foldEquity += 0.15; // Nits fold easily to pressure
            }
        }

        double raiseAmount = getBetSize();
        double raiseCost = raiseAmount - cpuPlayer.getBet();
        double evRaise = (foldEquity * impliedPot) + ((1.0 - foldEquity) * ((winProb * (impliedPot + raiseCost)) - ((1.0 - winProb) * raiseCost)));

        int decision = Player.FOLD;

        if (betCount == 0) {
            if (slowPlay && effectiveStrength >= 0.90 && street < Crupier.RIVER) {
                decision = Player.CHECK;
            } else if (street == Crupier.RIVER) {
                if (effectiveStrength >= 0.70) {
                    decision = Player.BET;
                } else if (skillLevel != Skill.RECREATIONAL && effectiveStrength < 0.30 && foldEquity > 0.25) {
                    // Only skilled players run pure bluffs on the river, and ONLY if opponent isn't a station
                    boolean holdsBlocker = (holeCard1.getRank() >= 10 || holeCard2.getRank() >= 10);
                    decision = holdsBlocker ? Player.BET : Player.CHECK;
                } else {
                    decision = Player.CHECK;
                }
            } else if (cBetInitiative && street == Crupier.FLOP && evRaise > -0.5) {
                decision = Player.BET;
                cBetInitiative = false;
            } else if (evRaise > 0 && evRaise > evCall && effectiveStrength > 0.60) {
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

            double betRatio = callCost / (pot > 0 ? pot : 1);

            // MDF / Fear Logic depends on Profile and Skill
            if (betRatio > 0.50) {
                if (currentProfile == Profile.STATION) {
                    evCall += 20; // Stations don't care about bet size
                } else if (currentProfile == Profile.NIT) {
                    evCall -= 30; // Nits overfold to big bets
                } else if (skillLevel == Skill.REGULAR && effectiveStrength < 0.75) {
                    evCall -= 15; // Regs respect sizing
                }
            }

            if (potCommitted) {
                decision = Player.CHECK;
                callCount++;
            } else if (evRaise > evCall && evRaise > 0 && effectiveStrength > 0.88 && currentProfile != Profile.STATION) {
                decision = Player.BET;
                callCount++;
            } else if ((evCall > 0) || (ppot > 1.5 * potOdds() && safeDraw)) {
                decision = Player.CHECK;
                callCount++;
            } else {
                decision = Player.FOLD;
            }
        }

        return decision;
    }

    private int calculatePreflopAction(int betCount) {
        Position pos = determinePosition();
        int v1 = holeCard1.getRank();
        int v2 = holeCard2.getRank();
        boolean suited = holeCard1.getSuit() == holeCard2.getSuit();
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean isPair = (high == low);

        // 1: Premium, 2: Strong, 3: Playable, 4: Marginal, 5: Trash
        int handTier = 5;

        if (isPair) {
            if (high >= 10) {
                handTier = 1; // QQ+
            } else if (high >= 8) {
                handTier = 2; // TT, JJ
            } else {
                handTier = 3; // 22-99
            }
        } else if (high == 12) {
            if (low >= 11) {
                handTier = 1; // AK
            } else if (low >= 9) {
                handTier = suited ? 2 : 3; // AQ, AJ
            } else if (suited) {
                handTier = 3; // A2s-ATs
            } else {
                handTier = 4; // A2o-ATo
            }
        } else if (high == 11) {
            if (low >= 10) {
                handTier = suited ? 2 : 3; // KQ
            } else if (suited && low >= 8) {
                handTier = 3;
            } else {
                handTier = 4;
            }
        } else if (suited && (high - low <= 2) && high >= 7) {
            handTier = 3; // Suited connectors
        } else if (high >= 9 && low >= 8) {
            handTier = 4; // Broadways
        }

        // Stations play almost anything playable regardless of action
        if (currentProfile == Profile.STATION && handTier <= 4 && betCount < 2) {
            return Player.CHECK;
        }

        if (betCount >= 2) {
            if (handTier == 1) {
                return Player.BET;
            }
            if (handTier == 2 && skillLevel == Skill.RECREATIONAL) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

        if (betCount == 1) {
            if (handTier == 1) {
                return Player.BET;
            }
            if (handTier <= 3) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }

        switch (pos) {
            case EARLY:
                if (handTier <= 2) {
                    return Player.BET;
                }
                if (handTier == 3 && currentProfile == Profile.STATION) {
                    return Player.CHECK;
                }
                return Player.FOLD;
            case MIDDLE:
                if (handTier <= 3) {
                    return Player.BET;
                }
                return Player.FOLD;
            case LATE:
                if (handTier <= 4 && currentProfile != Profile.NIT) {
                    return Player.BET;
                }
                return Player.FOLD;
            case BLINDS:
                if (handTier <= 3 && currentProfile != Profile.NIT) {
                    return Player.BET;
                }
                return Player.CHECK;
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
        for (int i = 1; i <= BOT_COMMUNITY_CARDS.size(); i++) {
            suits[BOT_COMMUNITY_CARDS.getCard(i).getSuit()]++;
        }
        for (int s : suits) {
            if (s == 2) {
                score += 1;
            }
            if (s >= 3) {
                score += 3;
            }
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

    // --- RESTORED: THE "EYES" OF THE BOT ---
    private OpponentTracker getPrimaryOpponentStats() {
        Player lastAggressor = GameFrame.getInstance().getCrupier().getLast_aggressor();
        if (lastAggressor != null && TRACKER_MEMORY.containsKey(lastAggressor.getNickname())) {
            return TRACKER_MEMORY.get(lastAggressor.getNickname());
        }
        return null;
    }

    // --- API CONVERTERS RESTORED (Prevents Crupier Compilation Errors) ---
    public static int coronaCardSuit2LokiCardSuit(Card carta) {
        return Bot.SUITS.indexOf(carta.getPalo());
    }

    // Overloaded to accept both primitive int and Integer object to be absolutely safe
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
