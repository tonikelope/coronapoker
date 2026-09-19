/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;

/**
 * Canonical resolution of CoronaPoker's two queued automatic actions.
 * Frontends own only the selected button; they must not reproduce these
 * betting rules.
 */
public final class AutoActionResolver {

    public enum QueuedAction { NONE, FOLD_OR_CHECK, CHECK_OR_CALL }
    public enum Target { NONE, FOLD, CHECK_OR_CALL, ALL_IN }

    private AutoActionResolver() {
    }

    public static Target resolve(QueuedAction queued,
            ActionControlState controls, boolean preflop, double bigBlind,
            boolean autoCallEnabled, double autoCallMaximum) {
        Objects.requireNonNull(controls, "controls");
        return resolve(queued, controls.callAmount(),
                controls.callAction() != ActionControlState.CallAction.DISABLED,
                controls.foldEnabled(), controls.allInEnabled(), preflop,
                controls.currentBet(), bigBlind, autoCallEnabled,
                autoCallMaximum, controls.playerStack());
    }

    public static Target resolve(QueuedAction queued, double callRequired,
            boolean checkOrCallEnabled, boolean foldEnabled,
            boolean allInEnabled, boolean preflop, double currentBet,
            double bigBlind, boolean autoCallEnabled,
            double autoCallMaximum, double playerStack) {
        Objects.requireNonNull(queued, "queued");
        requireAmount(callRequired, "callRequired");
        requireAmount(currentBet, "currentBet");
        requireAmount(bigBlind, "bigBlind");
        requireAmount(autoCallMaximum, "autoCallMaximum");
        requireAmount(playerStack, "playerStack");

        boolean free = MoneyMath.compare(0d, callRequired) == 0;
        if (queued == QueuedAction.FOLD_OR_CHECK) {
            if (checkOrCallEnabled && free) return Target.CHECK_OR_CALL;
            return foldEnabled ? Target.FOLD : Target.NONE;
        }
        if (queued != QueuedAction.CHECK_OR_CALL) return Target.NONE;

        boolean bigBlindOption = preflop
                && MoneyMath.compare(currentBet, bigBlind) == 0;
        double committed = Math.min(callRequired, playerStack);
        boolean withinAutoCall = autoCallEnabled
                && (MoneyMath.compare(0d, autoCallMaximum) == 0
                || MoneyMath.compare(committed, autoCallMaximum) <= 0);
        if (!free && !bigBlindOption && !withinAutoCall) return Target.NONE;
        if (checkOrCallEnabled) return Target.CHECK_OR_CALL;
        return allInEnabled ? Target.ALL_IN : Target.NONE;
    }

    private static void requireAmount(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label
                    + " must be a finite non-negative amount");
        }
    }
}
