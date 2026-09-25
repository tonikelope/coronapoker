/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** UI-neutral decimal quantization used by the classic engine and persistence. */
public final class MoneyMath {

    private MoneyMath() {
    }

    public static double clean(double value) {
        return clean(value, 2);
    }

    public static double clean(double value, int decimals) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("money value must be finite");
        }
        return new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    public static int compare(double left, double right) {
        return Double.compare(clean(left), clean(right));
    }
}
