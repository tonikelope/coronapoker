/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.BetRules;
import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.bot.context.BotPlayerView;
import com.tonikelope.coronapoker.bot.context.DealerView;

/**
 * Deterministic opponent benchmarks for the bot suite. These never make
 * adaptive decisions — they implement a single archetype consistently so the
 * production {@link Bot} can be measured against well-known strategy shapes
 * (calling station, rock, maniac, simple TAG).
 *
 * <p>
 * If the production bot cannot beat a calling station by a wide margin over
 * thousands of hands, something is fundamentally wrong with its value-betting /
 * range construction. These benchmarks exist precisely to catch that class of
 * regression without requiring a human to sit at the table.</p>
 */
public final class FixedStrategyBot extends Bot {

    private static final int HAND_GROUP_SIZE = 13 * 13 * 13 * 13 * 13;

    public enum Strategy {
        /**
         * Calling station: never folds when toCall > 0 (unless all-in), never
         * raises. Postflop always checks. A profitable target for value bets —
         * any competent bot should crush this by hundreds of bb/100.
         */
        STATION,
        /**
         * Rock: only voluntarily plays AA, KK, QQ, JJ, AKs, AKo. Always raises
         * preflop with these and goes for value. Folds everything else preflop.
         * Postflop bets and calls with top pair good kicker or better, folds
         * otherwise. A competent bot should steal the blinds enough to net
         * positive bb/100.
         */
        ROCK,
        /**
         * Maniac: raises 100% preflop when no bet (3-4x BB), shoves over any
         * raise with any two cards. Postflop bets pot. Catastrophic variance —
         * a competent bot should print money by trapping with strong hands.
         */
        MANIAC,
        /**
         * Simple TAG: opens a 330-combo range, c-bets air 65% of the time
         * as the preflop aggressor, continues half its non-premium opening
         * range against a 3-bet, and calls down with top pair or better. A reasonable
         * benchmark opponent — a competent bot should be at most slightly
         * negative against this archetype without opponent modeling.
         */
        TAG
    }

    private final Strategy strategy;
    private final java.util.Random localRng;
    private double lastTargetSize = 0;

    public FixedStrategyBot(BotPlayerView player, Strategy strategy, long seed) {
        super(player, new java.util.Random(seed ^ 0xA110CA7EL));
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
        double currentBet = d.getApuesta_actual();
        double toCall = currentBet - me.getBet();
        boolean preflop = (street == Crupier.PREFLOP);
        int hole1 = me.getHoleCard1Index();
        int hole2 = me.getHoleCard2Index();
        int rank1 = hole1 % 13;
        int rank2 = hole2 % 13;
        int high = Math.max(rank1, rank2);
        int low = Math.min(rank1, rank2);
        boolean pair = rank1 == rank2;
        boolean suited = (hole1 / 13) == (hole2 / 13);

        int decision = switch (strategy) {
            case STATION -> decideStation(toCall);
            case ROCK -> decideRock(preflop, toCall, pair, high, low, suited, d);
            case MANIAC -> decideManiac(preflop, toCall, d);
            case TAG -> decideTag(preflop, toCall, pair, high, low, suited, d);
        };
        if (decision == com.tonikelope.coronapoker.core.game.GamePlayerController.BET
                && !d.canPlayerRaise(me.getNickname())) {
            return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
        }
        return decision;
    }

    @Override
    public double getBetSize() {
        DealerView table = dealer();
        double wager = table.getApuesta_actual();
        double minimum = wager == 0 ? table.getCiega_grande()
                : BetRules.minRaiseTo(wager, table.getUltimo_raise(), table.getCiega_grande());
        return Math.max(lastTargetSize, minimum);
    }

    private int decideStation(double toCall) {
        if (toCall > 0f) {
            return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
        }
        return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
    }

    private int decideRock(boolean preflop, double toCall, boolean pair, int high, int low,
            boolean suited, DealerView d) {
        boolean premium = pair && high >= 9
                || (high == 12 && low == 11)
                || (high == 12 && low == 12);
        if (preflop) {
            if (premium) {
                if (toCall <= 0f) {
                    lastTargetSize = d.getCiega_grande() * 3.0f;
                    return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
                }
                lastTargetSize = Math.max(d.getApuesta_actual() * 3.0f,
                        d.getApuesta_actual() + d.getCiega_grande() * 4.0f);
                return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
            }
            if (toCall <= 0f) {
                return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
            }
            return com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;
        }
        if (premium) {
            if (toCall <= 0f) {
                lastTargetSize = d.getBote_total() * 0.65f;
                return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
            }
            return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
        }
        if (toCall <= 0f) {
            return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
        }
        return com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;
    }

    private int decideManiac(boolean preflop, double toCall, DealerView d) {
        if (preflop) {
            if (toCall <= 0f) {
                lastTargetSize = d.getCiega_grande() * 3.0f;
                return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
            }
            lastTargetSize = Math.max(d.getApuesta_actual() * 2.5f,
                    d.getApuesta_actual() + d.getCiega_grande() * 5.0f);
            return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
        }
        if (toCall <= 0f) {
            lastTargetSize = Math.max(d.getCiega_grande(), d.getBote_total() * 1.0f);
            return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
        }
        return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
    }

    private int decideTag(boolean preflop, double toCall, boolean pair, int high, int low,
            boolean suited, DealerView d) {
        boolean top25 = isTop25(pair, high, low, suited);
        int betCount = d.getConta_bet();
        if (preflop) {
            if (betCount == 0) {
                if (top25) {
                    lastTargetSize = Math.max(d.getCiega_grande() * 2.8f,
                            d.getApuesta_actual() + d.getCiega_grande() * 1.8f);
                    return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
                }
                return toCall <= 0f
                        ? com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK
                        : com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;
            }
            if (betCount >= 2) {
                if (pair && high >= 9) {
                    if (high >= 10) {
                        lastTargetSize = Math.max(d.getApuesta_actual() * 2.7f,
                                d.getApuesta_actual() + d.getCiega_grande() * 6.0f);
                        return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
                    }
                    return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
                }
                if (top25 && localRng.nextInt(100) >= 50) {
                    return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
                }
                return com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;
            }
            if (top25) {
                return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
            }
            return com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;
        }
        boolean topPairOrBetter = computeTopPairOrBetter(high, low, pair, d);
        boolean wasPreflopAggressor = player().getNickname().equals(d.getPreflopAggressorNick());
        if (toCall <= 0f) {
            if (d.getStreet() == Crupier.FLOP && wasPreflopAggressor
                    && localRng.nextInt(100) < 65) {
                lastTargetSize = d.getBote_total() * 0.55f;
                return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
            }
            if (topPairOrBetter) {
                lastTargetSize = d.getBote_total() * 0.65f;
                return com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
            }
            return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
        }
        if (topPairOrBetter) {
            return com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
        }
        return com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;
    }

    static boolean isTop25(boolean pair, int high, int low, boolean suited) {
        if (pair) {
            return true;
        }
        if (high == 12 && low >= 6) {
            return true;
        }
        if (high == 12 && suited) {
            return true;
        }
        if (high == 11 && low >= 8) {
            return true;
        }
        if (high == 11 && low >= 6 && suited) {
            return true;
        }
        if (high == 10 && low >= 8) {
            return true;
        }
        if (high == 10 && low >= 7 && suited) {
            return true;
        }
        if (high == 9 && low >= 8) {
            return true;
        }
        if (high == 9 && low >= 7 && suited) {
            return true;
        }
        if (suited && (high == 8 && low >= 6
                || high == 7 && low == 6
                || high == 6 && low == 5
                || high == 5 && low == 4)) {
            return true;
        }
        return false;
    }

    private boolean computeTopPairOrBetter(int high, int low, boolean pair, DealerView d) {
        int[] cards = new int[d.getBoardSize() + 2];
        cards[0] = player().getHoleCard1Index();
        cards[1] = player().getHoleCard2Index();
        int boardTop = -1;
        for (int i = 0; i < d.getBoardSize(); i++) {
            int boardCard = d.getBoardCardIndex(i);
            cards[i + 2] = boardCard;
            int br = boardCard % 13;
            if (br > boardTop) {
                boardTop = br;
            }
        }
        int handGroup = EVALUATOR.rankHand(cards) / HAND_GROUP_SIZE;
        if (handGroup >= 2) {
            return true;
        }
        if (pair && high >= boardTop) {
            return true;
        }
        if (!pair && (high == boardTop || low == boardTop)) {
            return true;
        }
        return false;
    }

    @Override
    public void resetBot() {
        super.resetBot();
        lastTargetSize = 0;
    }

}
