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
import com.tonikelope.coronapoker.Player;
import com.tonikelope.coronapoker.bot.context.BotPlayerView;
import com.tonikelope.coronapoker.bot.context.DealerView;

/**
 * Deterministic opponent benchmarks for the bot suite. These never make
 * adaptive decisions — they implement a single archetype consistently so
 * the production {@link Bot} can be measured against well-known strategy
 * shapes (calling station, rock, maniac, simple TAG).
 *
 * <p>If the production bot cannot beat a calling station by a wide margin
 * over thousands of hands, something is fundamentally wrong with its
 * value-betting / range construction. These benchmarks exist precisely to
 * catch that class of regression without requiring a human to sit at the
 * table.</p>
 */
public final class FixedStrategyBot extends Bot {

    public enum Strategy {
        /**
         * Calling station: never folds when toCall > 0 (unless all-in),
         * never raises. Postflop always checks. A profitable target for
         * value bets — any competent bot should crush this by hundreds of
         * bb/100.
         */
        STATION,
        /**
         * Rock: only voluntarily plays AA, KK, QQ, JJ, AKs, AKo. Always
         * raises preflop with these and goes for value. Folds everything
         * else preflop. Postflop bets and calls with top pair good kicker
         * or better, folds otherwise. A competent bot should steal the
         * blinds enough to net positive bb/100.
         */
        ROCK,
        /**
         * Maniac: raises 100% preflop when no bet (3-4x BB), shoves over
         * any raise with any two cards. Postflop bets pot. Catastrophic
         * variance — a competent bot should print money by trapping with
         * strong hands.
         */
        MANIAC,
        /**
         * Simple TAG: open-raises top ~25% preflop, c-bets 65% of flops,
         * folds to 3bet ~50% of the time, calls down with top-pair+. A
         * reasonable benchmark opponent — a competent bot should be at
         * most slightly negative against this archetype without opponent
         * modeling.
         */
        TAG
    }

    private final Strategy strategy;
    private final java.util.Random localRng;
    private double lastTargetSize = 0;

    public FixedStrategyBot(BotPlayerView player, Strategy strategy, long seed) {
        super(player);
        this.strategy = strategy;
        this.localRng = new java.util.Random(seed);
    }

    public Strategy strategy() {
        return strategy;
    }

    @Override
    public int calculateBotDecision(int opponentsCount) {
        DealerView d = dealer();
        BotPlayerView me = player();
        int street = d.getStreet();
        float currentBet = d.getApuesta_actual();
        float toCall = currentBet - me.getBet();
        boolean preflop = (street == Crupier.PREFLOP);
        int hole1 = me.getHoleCard1Index();
        int hole2 = me.getHoleCard2Index();
        int rank1 = hole1 % 13;
        int rank2 = hole2 % 13;
        int high = Math.max(rank1, rank2);
        int low = Math.min(rank1, rank2);
        boolean pair = rank1 == rank2;
        boolean suited = (hole1 / 13) == (hole2 / 13);

        switch (strategy) {
            case STATION:
                return decideStation(toCall);
            case ROCK:
                return decideRock(preflop, toCall, pair, high, low, suited, d);
            case MANIAC:
                return decideManiac(preflop, toCall, d);
            case TAG:
                return decideTag(preflop, toCall, pair, high, low, suited, d);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public float getBetSize() {
        return (float) lastTargetSize;
    }

    private int decideStation(float toCall) {
        if (toCall > 0f) {
            return Player.CHECK;
        }
        return Player.CHECK;
    }

    private int decideRock(boolean preflop, float toCall, boolean pair, int high, int low,
                           boolean suited, DealerView d) {
        boolean premium = pair && high >= 9
                || (high == 12 && low == 11)
                || (high == 12 && low == 12);
        if (preflop) {
            if (premium) {
                if (toCall <= 0f) {
                    lastTargetSize = d.getCiega_grande() * 3.0f;
                    return Player.BET;
                }
                lastTargetSize = Math.max(d.getApuesta_actual() * 3.0f,
                        d.getApuesta_actual() + d.getCiega_grande() * 4.0f);
                return Player.BET;
            }
            if (toCall <= 0f) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }
        if (premium) {
            if (toCall <= 0f) {
                lastTargetSize = d.getBote_total() * 0.65f;
                return Player.BET;
            }
            return Player.CHECK;
        }
        if (toCall <= 0f) {
            return Player.CHECK;
        }
        return Player.FOLD;
    }

    private int decideManiac(boolean preflop, float toCall, DealerView d) {
        if (preflop) {
            if (toCall <= 0f) {
                lastTargetSize = d.getCiega_grande() * 3.0f;
                return Player.BET;
            }
            lastTargetSize = Math.max(d.getApuesta_actual() * 2.5f,
                    d.getApuesta_actual() + d.getCiega_grande() * 5.0f);
            return Player.BET;
        }
        if (toCall <= 0f) {
            lastTargetSize = Math.max(d.getCiega_grande(), d.getBote_total() * 1.0f);
            return Player.BET;
        }
        return Player.CHECK;
    }

    private int decideTag(boolean preflop, float toCall, boolean pair, int high, int low,
                          boolean suited, DealerView d) {
        boolean top25 = isTop25(pair, high, low, suited);
        int betCount = d.getConta_bet();
        if (preflop) {
            if (toCall <= 0f) {
                if (top25) {
                    lastTargetSize = d.getCiega_grande() * 2.8f;
                    return Player.BET;
                }
                return Player.FOLD;
            }
            if (betCount >= 2) {
                if (pair && high >= 9) {
                    if (high >= 10) {
                        lastTargetSize = Math.max(d.getApuesta_actual() * 2.7f,
                                d.getApuesta_actual() + d.getCiega_grande() * 6.0f);
                        return Player.BET;
                    }
                    return Player.CHECK;
                }
                if (localRng.nextInt(100) < 50) {
                    return Player.FOLD;
                }
                return Player.FOLD;
            }
            if (top25) {
                return Player.CHECK;
            }
            return Player.FOLD;
        }
        boolean topPairOrBetter = computeTopPairOrBetter(high, low, pair, d);
        boolean wasPreflopAggressor = (d.getLast_aggressor() == null
                || d.getLast_aggressor().getNickname().equals(player().getNickname()));
        if (toCall <= 0f) {
            if (d.getStreet() == Crupier.FLOP && wasPreflopAggressor
                    && localRng.nextInt(100) < 65) {
                lastTargetSize = d.getBote_total() * 0.55f;
                return Player.BET;
            }
            if (topPairOrBetter) {
                lastTargetSize = d.getBote_total() * 0.65f;
                return Player.BET;
            }
            return Player.CHECK;
        }
        if (topPairOrBetter) {
            return Player.CHECK;
        }
        return Player.FOLD;
    }

    private boolean isTop25(boolean pair, int high, int low, boolean suited) {
        if (pair && high >= 4) {
            return true;
        }
        if (high == 12 && low >= 8) {
            return true;
        }
        if (high == 12 && low >= 4 && suited) {
            return true;
        }
        if (high == 11 && low >= 9) {
            return true;
        }
        if (high == 11 && low >= 8 && suited) {
            return true;
        }
        if (high == 10 && low >= 9) {
            return true;
        }
        if (high == 10 && low >= 8 && suited) {
            return true;
        }
        return false;
    }

    private boolean computeTopPairOrBetter(int high, int low, boolean pair, DealerView d) {
        int boardTop = -1;
        for (int i = 0; i < d.getBoardSize(); i++) {
            int br = d.getBoardCardIndex(i) % 13;
            if (br > boardTop) {
                boardTop = br;
            }
        }
        if (pair && high >= boardTop) {
            return true;
        }
        if (!pair && (high == boardTop || low == boardTop) && Math.max(high, low) >= 9) {
            return true;
        }
        return false;
    }

    @Override
    public void resetBot() {
        super.resetBot();
        lastTargetSize = 0;
    }

    static {
        if (GameFrame.CIEGA_PEQUEÑA <= 0f) {
            GameFrame.CIEGA_PEQUEÑA = 1.0f;
        }
    }
}
