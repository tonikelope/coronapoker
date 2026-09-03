/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Legal local action controls calculated by the canonical betting state. */
public record ActionControlState(boolean foldEnabled, CallAction callAction,
        double callAmount, RaiseAction raiseAction, double raiseMinimum,
        double raiseMaximum, double raiseStep, double raiseAmount,
        boolean allInEnabled, boolean showCards) {

    public enum CallAction { DISABLED, CHECK, CALL }
    public enum RaiseAction { DISABLED, BET, RAISE, RERAISE }

    public ActionControlState {
        java.util.Objects.requireNonNull(callAction, "callAction");
        java.util.Objects.requireNonNull(raiseAction, "raiseAction");
        requireFinite(callAmount, "callAmount");
        requireFinite(raiseMinimum, "raiseMinimum");
        requireFinite(raiseMaximum, "raiseMaximum");
        requireFinite(raiseStep, "raiseStep");
        requireFinite(raiseAmount, "raiseAmount");
        if (callAmount < 0d || raiseMinimum < 0d || raiseMaximum < 0d
                || raiseStep < 0d || raiseAmount < 0d) {
            throw new IllegalArgumentException("Action amounts cannot be negative");
        }
    }

    public static ActionControlState disabled() {
        return new ActionControlState(false, CallAction.DISABLED, 0d,
                RaiseAction.DISABLED, 0d, 0d, 0d, 0d,
                false, false);
    }

    public static ActionControlState forTurn(double currentBet,
            double lastRaise, double bigBlind, double smallBlind,
            double playerBet, double playerStack, int playersAbleToBet,
            boolean playerMayRaise, int raiseCount) {
        BigDecimal current = amount(currentBet);
        BigDecimal bet = amount(playerBet);
        BigDecimal stack = amount(playerStack);
        BigDecimal big = amount(bigBlind);
        BigDecimal small = amount(smallBlind > 0d ? smallBlind : bigBlind / 2d);
        if (big.signum() <= 0 || small.signum() <= 0) {
            throw new IllegalArgumentException("Blinds must be positive");
        }
        BigDecimal call = current.subtract(bet).max(BigDecimal.ZERO);
        boolean canCall = call.compareTo(stack) < 0;
        CallAction callAction = !canCall ? CallAction.DISABLED
                : call.signum() == 0 ? CallAction.CHECK : CallAction.CALL;

        BigDecimal minimumRaise = amount(Math.max(lastRaise, bigBlind));
        boolean raisePossible = playerMayRaise && playersAbleToBet > 1
                && ((current.signum() == 0 && big.compareTo(stack) < 0)
                || (current.signum() > 0
                && call.add(minimumRaise).compareTo(stack) < 0));
        BigDecimal minimum = BigDecimal.ZERO;
        BigDecimal maximum = BigDecimal.ZERO;
        RaiseAction raiseAction = RaiseAction.DISABLED;
        if (raisePossible) {
            maximum = bet.add(stack).divide(small, 0, RoundingMode.FLOOR)
                    .multiply(small).subtract(current).max(BigDecimal.ZERO);
            if (current.signum() == 0) {
                minimum = big;
                raiseAction = RaiseAction.BET;
            } else {
                minimum = current.add(minimumRaise)
                        .divide(small, 0, RoundingMode.CEILING)
                        .multiply(small).subtract(current).max(BigDecimal.ZERO);
                raiseAction = raiseCount > 0
                        ? RaiseAction.RERAISE : RaiseAction.RAISE;
            }
            if (minimum.compareTo(maximum) >= 0) {
                minimum = BigDecimal.ZERO;
                maximum = BigDecimal.ZERO;
                raiseAction = RaiseAction.DISABLED;
            }
        }

        boolean allIn = !((playersAbleToBet == 1 || !playerMayRaise)
                && call.compareTo(stack) < 0);
        return new ActionControlState(true, callAction, call.doubleValue(),
                raiseAction, minimum.doubleValue(), maximum.doubleValue(),
                raiseAction == RaiseAction.DISABLED ? 0d : small.doubleValue(),
                minimum.doubleValue(), allIn, false);
    }

    private static BigDecimal amount(double value) {
        requireFinite(value, "amount");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
